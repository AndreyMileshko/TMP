package com.tmp.production.application;

import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedMaterialLine;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort.MaterialReferenceEntry;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort.WarehouseCatalogEntry;
import com.tmp.production.domain.AggregatedMaterialRequirement;
import com.tmp.production.domain.InvalidProductionWarehouseScopeException;
import com.tmp.production.domain.MaterialAvailabilityCheckResult;
import com.tmp.production.domain.MaterialAvailabilityLine;
import com.tmp.production.domain.MaterialAvailabilityLineStatus;
import com.tmp.production.domain.MaterialAvailabilityOverallStatus;
import com.tmp.production.domain.MaterialCheckNotAllowedException;
import com.tmp.production.domain.MaterialPlanningSource;
import com.tmp.production.domain.OrderProductionView;
import com.tmp.production.domain.OrderProductionViewStatus;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SpecificationMaterialIdentity;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Side-effect-free current material availability calculation (Production Spec §18.1).
 *
 * <p>Produces the same semantics as {@link CheckMaterialAvailabilityService} but does not append
 * {@code MATERIALS_CHECKED} history and does not mutate Production/Warehouse.
 */
public final class CurrentMaterialAvailabilityQueryService {

    private final ProductionOrderViewService orderViewService;
    private final ProductionFoundationQueryService foundationQuery;
    private final WarehouseAvailabilityQueryPort warehouseQuery;
    private final ProductionWarehouseScope warehouseScope;
    private final SpecificationMaterialRequirementCalculator requirementCalculator;
    private final MaterialReferenceResolver materialReferenceResolver;
    private final Clock clock;

    public CurrentMaterialAvailabilityQueryService(
            ProductionOrderViewService orderViewService,
            ProductionFoundationQueryService foundationQuery,
            WarehouseAvailabilityQueryPort warehouseQuery,
            ProductionWarehouseScope warehouseScope,
            Clock clock) {
        this(
                orderViewService,
                foundationQuery,
                warehouseQuery,
                warehouseScope,
                new SpecificationMaterialRequirementCalculator(),
                new MaterialReferenceResolver(),
                clock);
    }

    CurrentMaterialAvailabilityQueryService(
            ProductionOrderViewService orderViewService,
            ProductionFoundationQueryService foundationQuery,
            WarehouseAvailabilityQueryPort warehouseQuery,
            ProductionWarehouseScope warehouseScope,
            SpecificationMaterialRequirementCalculator requirementCalculator,
            MaterialReferenceResolver materialReferenceResolver,
            Clock clock) {
        this.orderViewService = Objects.requireNonNull(orderViewService, "orderViewService");
        this.foundationQuery = Objects.requireNonNull(foundationQuery, "foundationQuery");
        this.warehouseQuery = Objects.requireNonNull(warehouseQuery, "warehouseQuery");
        this.warehouseScope = Objects.requireNonNull(warehouseScope, "warehouseScope");
        this.requirementCalculator =
                Objects.requireNonNull(requirementCalculator, "requirementCalculator");
        this.materialReferenceResolver =
                Objects.requireNonNull(materialReferenceResolver, "materialReferenceResolver");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Calculates current material availability for one order.
     *
     * @throws MaterialCheckNotAllowedException when Production View is not {@code IN_PRODUCTION}
     */
    public MaterialAvailabilityCheckResult evaluate(SourceOrderId sourceOrderId) {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");

        OrderProductionView view = orderViewService.getOrderProductionView(sourceOrderId);
        if (view.status() != OrderProductionViewStatus.IN_PRODUCTION) {
            throw new MaterialCheckNotAllowedException(sourceOrderId, view.status());
        }

        validateWarehouseScope();

        // Material requirements are computed from item-owned states in the frozen specification.
        List<ResolvedMaterialLine> allMaterialLines = new ArrayList<>();
        for (ProductionItemState state : orderViewService.listItemStates(sourceOrderId)) {
            allMaterialLines.addAll(foundationQuery.materialLines(state));
        }

        List<AggregatedMaterialRequirement> requirements =
                requirementCalculator.aggregate(allMaterialLines);

        List<MaterialReferenceEntry> materialCatalog = warehouseQuery.listMaterialReferences();

        List<MaterialAvailabilityLine> lines = new ArrayList<>(requirements.size());
        for (AggregatedMaterialRequirement requirement : requirements) {
            lines.add(buildLine(requirement, materialCatalog));
        }

        return new MaterialAvailabilityCheckResult(
                sourceOrderId,
                clock.instant(),
                resolveOverallStatus(lines),
                lines);
    }

    private void validateWarehouseScope() {
        List<WarehouseCatalogEntry> warehouses = warehouseQuery.listWarehouses();
        validateWarehouse(warehouses, warehouseScope.mainWarehouseId());
        validateWarehouse(warehouses, warehouseScope.productionWarehouseId());
    }

    private static void validateWarehouse(
            List<WarehouseCatalogEntry> warehouses, UUID warehouseId) {
        WarehouseCatalogEntry entry =
                warehouses.stream()
                        .filter(candidate -> candidate.warehouseId().equals(warehouseId))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        InvalidProductionWarehouseScopeException.warehouseNotFound(
                                                warehouseId));
        if (!entry.active()) {
            throw InvalidProductionWarehouseScopeException.warehouseInactive(warehouseId);
        }
    }

    private MaterialAvailabilityLine buildLine(
            AggregatedMaterialRequirement requirement, List<MaterialReferenceEntry> catalog) {
        SpecificationMaterialIdentity identity = requirement.identity();
        MaterialReferenceResolver.Result resolution =
                materialReferenceResolver.resolve(identity, catalog);

        if (resolution.status() == MaterialReferenceResolver.ResolutionStatus.UNRESOLVED) {
            return unresolvedLine(requirement, MaterialAvailabilityLineStatus.MATERIAL_UNRESOLVED);
        }
        if (resolution.status() == MaterialReferenceResolver.ResolutionStatus.AMBIGUOUS) {
            return unresolvedLine(requirement, MaterialAvailabilityLineStatus.MATERIAL_AMBIGUOUS);
        }

        UUID materialReferenceId = resolution.materialReferenceId();
        BigDecimal mainAvailable =
                warehouseQuery.availableQuantity(
                        materialReferenceId, warehouseScope.mainWarehouseId());
        BigDecimal productionAvailable =
                warehouseQuery.availableQuantity(
                        materialReferenceId, warehouseScope.productionWarehouseId());
        BigDecimal totalAvailable = mainAvailable.add(productionAvailable);
        BigDecimal deficit = deficit(requirement.requiredQuantity(), totalAvailable);
        MaterialAvailabilityLineStatus status =
                deficit.signum() > 0 ? MaterialAvailabilityLineStatus.INSUFFICIENT : MaterialAvailabilityLineStatus.AVAILABLE;

        return new MaterialAvailabilityLine(
                identity.materialCode(),
                requirement.materialName(),
                identity.color(),
                identity.unitOfMeasure(),
                materialReferenceId,
                requirement.requiredQuantity(),
                mainAvailable,
                productionAvailable,
                totalAvailable,
                deficit,
                status,
                MaterialPlanningSource.SPECIFICATION);
    }

    private static MaterialAvailabilityLine unresolvedLine(
            AggregatedMaterialRequirement requirement, MaterialAvailabilityLineStatus status) {
        return new MaterialAvailabilityLine(
                requirement.identity().materialCode(),
                requirement.materialName(),
                requirement.identity().color(),
                requirement.identity().unitOfMeasure(),
                null,
                requirement.requiredQuantity(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                requirement.requiredQuantity(),
                requirement.requiredQuantity(),
                status,
                MaterialPlanningSource.SPECIFICATION);
    }

    private static MaterialAvailabilityOverallStatus resolveOverallStatus(
            List<MaterialAvailabilityLine> lines) {
        boolean hasUnresolved = false;
        boolean hasDeficit = false;
        for (MaterialAvailabilityLine line : lines) {
            if (line.status() == MaterialAvailabilityLineStatus.MATERIAL_UNRESOLVED
                    || line.status() == MaterialAvailabilityLineStatus.MATERIAL_AMBIGUOUS) {
                hasUnresolved = true;
            } else if (line.status() == MaterialAvailabilityLineStatus.INSUFFICIENT) {
                hasDeficit = true;
            }
        }
        if (hasUnresolved) {
            return MaterialAvailabilityOverallStatus.HAS_UNRESOLVED_MATERIALS;
        }
        if (hasDeficit) {
            return MaterialAvailabilityOverallStatus.HAS_DEFICIT;
        }
        return MaterialAvailabilityOverallStatus.ALL_AVAILABLE;
    }

    private static BigDecimal deficit(BigDecimal required, BigDecimal available) {
        BigDecimal difference = required.subtract(available);
        return difference.signum() > 0 ? difference : BigDecimal.ZERO;
    }
}

