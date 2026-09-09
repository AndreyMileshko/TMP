package com.tmp.production.application;

import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedMaterialLine;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort.MaterialReferenceEntry;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort.WarehouseCatalogEntry;
import com.tmp.production.domain.AggregatedMaterialRequirement;
import com.tmp.production.domain.InvalidProductionDestinationWarehouseException;
import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.MaterialRequirement;
import com.tmp.production.domain.MaterialRequirementId;
import com.tmp.production.domain.MaterialRequirementLine;
import com.tmp.production.domain.MaterialRequirementLineId;
import com.tmp.production.domain.MaterialRequirementNotAllowedException;
import com.tmp.production.domain.MaterialRequirementNotReadyException;
import com.tmp.production.domain.MaterialRequirementSelectionException;
import com.tmp.production.domain.OrderProductionView;
import com.tmp.production.domain.OrderProductionViewStatus;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionStatus;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationMaterialIdentity;
import com.tmp.production.domain.repository.MaterialRequirementRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Application use cases for Production-owned editable Material Requirements (Stage 3.5.9).
 *
 * <p>prepare → persist → edit/read. Submit / Warehouse routing is Stage 3.5.10.
 */
public final class MaterialRequirementService {

    private final ProductionOrderViewService orderViewService;
    private final ProductionFoundationQueryService foundationQuery;
    private final ProductionDestinationWarehouse destinationWarehouse;
    private final WarehouseAvailabilityQueryPort warehouseQuery;
    private final MaterialRequirementRepository requirementRepository;
    private final SpecificationMaterialRequirementCalculator requirementCalculator;
    private final MaterialReferenceResolver materialReferenceResolver;
    private final Clock clock;

    public MaterialRequirementService(
            ProductionOrderViewService orderViewService,
            ProductionFoundationQueryService foundationQuery,
            ProductionDestinationWarehouse destinationWarehouse,
            WarehouseAvailabilityQueryPort warehouseQuery,
            MaterialRequirementRepository requirementRepository,
            Clock clock) {
        this(
                orderViewService,
                foundationQuery,
                destinationWarehouse,
                warehouseQuery,
                requirementRepository,
                new SpecificationMaterialRequirementCalculator(),
                new MaterialReferenceResolver(),
                clock);
    }

    MaterialRequirementService(
            ProductionOrderViewService orderViewService,
            ProductionFoundationQueryService foundationQuery,
            ProductionDestinationWarehouse destinationWarehouse,
            WarehouseAvailabilityQueryPort warehouseQuery,
            MaterialRequirementRepository requirementRepository,
            SpecificationMaterialRequirementCalculator requirementCalculator,
            MaterialReferenceResolver materialReferenceResolver,
            Clock clock) {
        this.orderViewService = Objects.requireNonNull(orderViewService, "orderViewService");
        this.foundationQuery = Objects.requireNonNull(foundationQuery, "foundationQuery");
        this.destinationWarehouse =
                Objects.requireNonNull(destinationWarehouse, "destinationWarehouse");
        this.warehouseQuery = Objects.requireNonNull(warehouseQuery, "warehouseQuery");
        this.requirementRepository =
                Objects.requireNonNull(requirementRepository, "requirementRepository");
        this.requirementCalculator =
                Objects.requireNonNull(requirementCalculator, "requirementCalculator");
        this.materialReferenceResolver =
                Objects.requireNonNull(materialReferenceResolver, "materialReferenceResolver");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Creates and persists a DRAFT Material Requirement from selected Production Item states and
     * their frozen Specifications.
     */
    public MaterialRequirement prepareMaterialRequirement(
            SourceOrderId orderId, List<SourceOrderItemId> selectedOrderItemIds) {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(selectedOrderItemIds, "selectedOrderItemIds");

        OrderProductionView view = orderViewService.getOrderProductionView(orderId);
        if (view.status() != OrderProductionViewStatus.IN_PRODUCTION) {
            throw new MaterialRequirementNotAllowedException(orderId, view.status());
        }

        if (selectedOrderItemIds.isEmpty()) {
            throw new MaterialRequirementSelectionException(
                    "Material requirement selection must not be empty");
        }

        validateDestinationWarehouse();

        List<ProductionItemState> itemStates = orderViewService.listItemStates(orderId);
        Map<SourceOrderItemId, ProductionItemState> statesByItemId =
                itemStates.stream()
                        .collect(
                                Collectors.toMap(
                                        ProductionItemState::sourceOrderItemId,
                                        Function.identity(),
                                        (left, right) -> left,
                                        LinkedHashMap::new));

        List<ProductionItemState> selectedStates = new ArrayList<>(selectedOrderItemIds.size());
        Set<SourceOrderItemId> seen = new LinkedHashSet<>();
        for (SourceOrderItemId selectedId : selectedOrderItemIds) {
            Objects.requireNonNull(selectedId, "selectedOrderItemId");
            if (!seen.add(selectedId)) {
                continue;
            }
            ProductionItemState state = statesByItemId.get(selectedId);
            if (state == null) {
                throw new MaterialRequirementSelectionException(
                        "Selected order item does not belong to order "
                                + orderId
                                + ": "
                                + selectedId);
            }
            if (state.status() != ProductionStatus.IN_PRODUCTION
                    && state.status() != ProductionStatus.PARTIALLY_RELEASED) {
                throw new MaterialRequirementSelectionException(
                        "Selected order item is not eligible for material requirement: "
                                + selectedId
                                + ", status="
                                + state.status());
            }
            selectedStates.add(state);
        }

        List<ResolvedMaterialLine> collectedLines = new ArrayList<>();
        Map<SpecificationMaterialIdentity, Set<SourceOrderItemId>> contributorsByIdentity =
                new LinkedHashMap<>();
        for (ProductionItemState state : selectedStates) {
            List<ResolvedMaterialLine> materialLines = foundationQuery.materialLines(state);
            for (ResolvedMaterialLine line : materialLines) {
                collectedLines.add(line);
                SpecificationMaterialIdentity identity =
                        SpecificationMaterialIdentity.of(
                                line.materialCode(), line.color(), line.unitOfMeasure());
                contributorsByIdentity
                        .computeIfAbsent(identity, ignored -> new LinkedHashSet<>())
                        .add(state.sourceOrderItemId());
            }
        }

        List<AggregatedMaterialRequirement> aggregates =
                requirementCalculator.aggregate(collectedLines);
        List<MaterialReferenceEntry> catalog = warehouseQuery.listMaterialReferences();
        Map<UUID, MaterialReferenceEntry> catalogById =
                catalog.stream()
                        .collect(
                                Collectors.toMap(
                                        MaterialReferenceEntry::materialReferenceId,
                                        Function.identity(),
                                        (left, right) -> left));

        List<MaterialRequirementLine> lines = new ArrayList<>();
        for (AggregatedMaterialRequirement aggregate : aggregates) {
            if (aggregate.requiredQuantity().signum() <= 0) {
                continue;
            }
            MaterialReferenceResolver.Result resolution =
                    materialReferenceResolver.resolve(aggregate.identity(), catalog);
            if (resolution.status() == MaterialReferenceResolver.ResolutionStatus.UNRESOLVED) {
                throw new MaterialRequirementNotReadyException(
                        orderId,
                        aggregate.identity(),
                        MaterialRequirementNotReadyException.Problem.UNRESOLVED);
            }
            if (resolution.status() == MaterialReferenceResolver.ResolutionStatus.AMBIGUOUS) {
                throw new MaterialRequirementNotReadyException(
                        orderId,
                        aggregate.identity(),
                        MaterialRequirementNotReadyException.Problem.AMBIGUOUS);
            }

            MaterialReferenceEntry catalogEntry = catalogById.get(resolution.materialReferenceId());
            String materialName =
                    catalogEntry != null ? catalogEntry.name() : aggregate.materialName();
            Set<SourceOrderItemId> contributors =
                    contributorsByIdentity.getOrDefault(
                            aggregate.identity(), Set.of());

            lines.add(
                    MaterialRequirementLine.create(
                            MaterialReferenceId.of(resolution.materialReferenceId()),
                            aggregate.identity().materialCode(),
                            materialName,
                            aggregate.identity().color(),
                            aggregate.identity().unitOfMeasure(),
                            aggregate.requiredQuantity(),
                            contributors));
        }

        Instant now = clock.instant();
        MaterialRequirement requirement =
                MaterialRequirement.create(
                        orderId,
                        destinationWarehouse.productionWarehouseId(),
                        now,
                        lines);
        return requirementRepository.save(requirement);
    }

    public Optional<MaterialRequirement> findById(MaterialRequirementId id) {
        Objects.requireNonNull(id, "id");
        return requirementRepository.findById(id);
    }

    public MaterialRequirement changeQuantity(
            MaterialRequirementId id, MaterialRequirementLineId lineId, BigDecimal quantity) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(lineId, "lineId");
        Objects.requireNonNull(quantity, "quantity");
        MaterialRequirement requirement =
                requirementRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Material requirement not found: " + id));
        MaterialRequirement edited =
                requirement.changeLineQuantity(lineId, quantity, clock.instant());
        return requirementRepository.save(edited);
    }

    private void validateDestinationWarehouse() {
        UUID warehouseId = destinationWarehouse.productionWarehouseId();
        List<WarehouseCatalogEntry> warehouses = warehouseQuery.listWarehouses();
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
}
