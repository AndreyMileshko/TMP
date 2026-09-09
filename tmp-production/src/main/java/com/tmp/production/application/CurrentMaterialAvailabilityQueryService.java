package com.tmp.production.application;

import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedMaterialLine;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort.MaterialReferenceEntry;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort.WarehouseCatalogEntry;
import com.tmp.production.domain.AggregatedMaterialRequirement;
import com.tmp.production.domain.InvalidProductionDestinationWarehouseException;
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
 * <p>Authoritative calculator used by both Public Query and explicit Check. Does not append
 * {@code MATERIALS_CHECKED} history and does not mutate Production/Warehouse.
 *
 * <p>Informational only: production stock plus sum of AVAILABLE stock across all other active
 * warehouses (no fixed main warehouse; no transfer recommendation).
 */
public final class CurrentMaterialAvailabilityQueryService {

    private final ProductionOrderViewService orderViewService;
    private final ProductionFoundationQueryService foundationQuery;
    private final WarehouseAvailabilityQueryPort warehouseQuery;
    private final ProductionDestinationWarehouse destinationWarehouse;
    private final SpecificationMaterialRequirementCalculator requirementCalculator;
    private final MaterialReferenceResolver materialReferenceResolver;
    private final Clock clock;

    public CurrentMaterialAvailabilityQueryService(
            ProductionOrderViewService orderViewService,
            ProductionFoundationQueryService foundationQuery,
            WarehouseAvailabilityQueryPort warehouseQuery,
            ProductionDestinationWarehouse destinationWarehouse,
            Clock clock) {
        this(
                orderViewService,
                foundationQuery,
                warehouseQuery,
                destinationWarehouse,
                new SpecificationMaterialRequirementCalculator(),
                new MaterialReferenceResolver(),
                clock);
    }

    CurrentMaterialAvailabilityQueryService(
            ProductionOrderViewService orderViewService,
            ProductionFoundationQueryService foundationQuery,
            WarehouseAvailabilityQueryPort warehouseQuery,
            ProductionDestinationWarehouse destinationWarehouse,
            SpecificationMaterialRequirementCalculator requirementCalculator,
            MaterialReferenceResolver materialReferenceResolver,
            Clock clock) {
        this.orderViewService = Objects.requireNonNull(orderViewService, "orderViewService");
        this.foundationQuery = Objects.requireNonNull(foundationQuery, "foundationQuery");
        this.warehouseQuery = Objects.requireNonNull(warehouseQuery, "warehouseQuery");
        this.destinationWarehouse =
                Objects.requireNonNull(destinationWarehouse, "destinationWarehouse");
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

        List<WarehouseCatalogEntry> warehouses = warehouseQuery.listWarehouses();
        validateDestinationWarehouse(warehouses);

        List<ResolvedMaterialLine> allMaterialLines = new ArrayList<>();
        for (ProductionItemState state : orderViewService.listItemStates(sourceOrderId)) {
            allMaterialLines.addAll(foundationQuery.materialLines(state));
        }

        List<AggregatedMaterialRequirement> requirements =
                requirementCalculator.aggregate(allMaterialLines);

        List<MaterialReferenceEntry> materialCatalog = warehouseQuery.listMaterialReferences();

        List<MaterialAvailabilityLine> lines = new ArrayList<>(requirements.size());
        for (AggregatedMaterialRequirement requirement : requirements) {
            lines.add(buildLine(requirement, materialCatalog, warehouses));
        }

        return new MaterialAvailabilityCheckResult(
                sourceOrderId, clock.instant(), resolveOverallStatus(lines), lines);
    }

    private void validateDestinationWarehouse(List<WarehouseCatalogEntry> warehouses) {
        UUID warehouseId = destinationWarehouse.productionWarehouseId();
        WarehouseCatalogEntry entry =
                warehouses.stream()
                        .filter(candidate -> candidate.warehouseId().equals(warehouseId))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        InvalidProductionDestinationWarehouseException
                                                .warehouseNotFound(warehouseId));
        if (!entry.active()) {
            throw InvalidProductionDestinationWarehouseException.warehouseInactive(warehouseId);
        }
    }

    private MaterialAvailabilityLine buildLine(
            AggregatedMaterialRequirement requirement,
            List<MaterialReferenceEntry> catalog,
            List<WarehouseCatalogEntry> warehouses) {
        SpecificationMaterialIdentity identity = requirement.identity();
        MaterialReferenceResolver.Result resolution =
                materialReferenceResolver.resolve(identity, toCatalog(catalog));

        if (resolution.status() == MaterialReferenceResolver.ResolutionStatus.UNRESOLVED) {
            return unresolvedLine(requirement, MaterialAvailabilityLineStatus.MATERIAL_UNRESOLVED);
        }
        if (resolution.status() == MaterialReferenceResolver.ResolutionStatus.AMBIGUOUS) {
            return unresolvedLine(requirement, MaterialAvailabilityLineStatus.MATERIAL_AMBIGUOUS);
        }

        UUID materialReferenceId = resolution.materialReferenceId();
        UUID destinationWarehouseId = destinationWarehouse.productionWarehouseId();
        BigDecimal productionAvailable =
                warehouseQuery.availableQuantity(materialReferenceId, destinationWarehouseId);
        BigDecimal supplyAvailable =
                sumSupplyAvailable(materialReferenceId, destinationWarehouseId, warehouses);
        BigDecimal totalAvailable = productionAvailable.add(supplyAvailable);
        BigDecimal deficit = deficit(requirement.requiredQuantity(), totalAvailable);
        MaterialAvailabilityLineStatus status =
                deficit.signum() > 0
                        ? MaterialAvailabilityLineStatus.INSUFFICIENT
                        : MaterialAvailabilityLineStatus.AVAILABLE;

        return new MaterialAvailabilityLine(
                identity.materialCode(),
                requirement.materialName(),
                identity.color(),
                identity.unitOfMeasure(),
                materialReferenceId,
                requirement.requiredQuantity(),
                supplyAvailable,
                productionAvailable,
                totalAvailable,
                deficit,
                status,
                MaterialPlanningSource.SPECIFICATION);
    }

    private BigDecimal sumSupplyAvailable(
            UUID materialReferenceId,
            UUID destinationWarehouseId,
            List<WarehouseCatalogEntry> warehouses) {
        BigDecimal sum = BigDecimal.ZERO;
        for (WarehouseCatalogEntry warehouse : warehouses) {
            if (!warehouse.active()) {
                continue;
            }
            if (warehouse.warehouseId().equals(destinationWarehouseId)) {
                continue;
            }
            sum =
                    sum.add(
                            warehouseQuery.availableQuantity(
                                    materialReferenceId, warehouse.warehouseId()));
        }
        return sum;
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
                BigDecimal.ZERO,
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

    private static List<MaterialReferenceResolver.CatalogEntry> toCatalog(
            List<MaterialReferenceEntry> catalog) {
        return catalog.stream()
                .map(
                        entry ->
                                new MaterialReferenceResolver.CatalogEntry(
                                        entry.materialReferenceId(),
                                        entry.article(),
                                        entry.color(),
                                        entry.unitOfMeasure()))
                .toList();
    }
}
