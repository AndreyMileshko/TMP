package com.tmp.production.application;

import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedMaterialLine;
import com.tmp.production.application.port.WarehouseReferenceQueryPort;
import com.tmp.production.application.port.WarehouseReferenceQueryPort.MaterialReferenceEntry;
import com.tmp.production.application.port.WarehouseReferenceQueryPort.WarehouseReferenceEntry;
import com.tmp.production.domain.InvalidProductionDestinationWarehouseException;
import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.MaterialRequirement;
import com.tmp.production.domain.MaterialRequirementId;
import com.tmp.production.domain.MaterialRequirementLine;
import com.tmp.production.domain.MaterialRequirementLineId;
import com.tmp.production.domain.MaterialRequirementNotAllowedException;
import com.tmp.production.domain.MaterialRequirementNotReadyException;
import com.tmp.production.domain.MaterialRequirementOptimisticLockException;
import com.tmp.production.domain.MaterialRequirementSelectionException;
import com.tmp.production.domain.MaterialRequirementSourceItem;
import com.tmp.production.domain.MaterialRequirementSourceItemKey;
import com.tmp.production.domain.OrderProductionView;
import com.tmp.production.domain.OrderProductionViewStatus;
import com.tmp.production.domain.OrderQuantityModeSetting;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionQuantityMode;
import com.tmp.production.domain.ProductionStatus;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.repository.MaterialRequirementRepository;
import com.tmp.production.domain.repository.OrderQuantityModeRepository;
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

/**
 * Application use cases for Production-owned editable Material Requirements (Stage 3.5.9 / Stage 7
 * Phase 2 cross-order product coverage).
 *
 * <p>prepare → persist → edit/read. Submit / Warehouse routing is Stage 3.5.10.
 */
public final class MaterialRequirementService {

    private final ProductionOrderViewService orderViewService;
    private final ProductionFoundationQueryService foundationQuery;
    private final ProductionDestinationWarehouse destinationWarehouse;
    private final WarehouseReferenceQueryPort warehouseReferences;
    private final MaterialRequirementRepository requirementRepository;
    private final OrderQuantityModeRepository quantityModeRepository;
    private final MaterialRequirementCoverageService coverageService;
    private final SpecificationMaterialRequirementCalculator requirementCalculator;
    private final MaterialReferenceResolver materialReferenceResolver;
    private final Clock clock;

    public MaterialRequirementService(
            ProductionOrderViewService orderViewService,
            ProductionFoundationQueryService foundationQuery,
            ProductionDestinationWarehouse destinationWarehouse,
            WarehouseReferenceQueryPort warehouseReferences,
            MaterialRequirementRepository requirementRepository,
            OrderQuantityModeRepository quantityModeRepository,
            MaterialRequirementCoverageService coverageService,
            Clock clock) {
        this(
                orderViewService,
                foundationQuery,
                destinationWarehouse,
                warehouseReferences,
                requirementRepository,
                quantityModeRepository,
                coverageService,
                new SpecificationMaterialRequirementCalculator(),
                new MaterialReferenceResolver(),
                clock);
    }

    MaterialRequirementService(
            ProductionOrderViewService orderViewService,
            ProductionFoundationQueryService foundationQuery,
            ProductionDestinationWarehouse destinationWarehouse,
            WarehouseReferenceQueryPort warehouseReferences,
            MaterialRequirementRepository requirementRepository,
            OrderQuantityModeRepository quantityModeRepository,
            MaterialRequirementCoverageService coverageService,
            SpecificationMaterialRequirementCalculator requirementCalculator,
            MaterialReferenceResolver materialReferenceResolver,
            Clock clock) {
        this.orderViewService = Objects.requireNonNull(orderViewService, "orderViewService");
        this.foundationQuery = Objects.requireNonNull(foundationQuery, "foundationQuery");
        this.destinationWarehouse =
                Objects.requireNonNull(destinationWarehouse, "destinationWarehouse");
        this.warehouseReferences =
                Objects.requireNonNull(warehouseReferences, "warehouseReferences");
        this.requirementRepository =
                Objects.requireNonNull(requirementRepository, "requirementRepository");
        this.quantityModeRepository =
                Objects.requireNonNull(quantityModeRepository, "quantityModeRepository");
        this.coverageService = Objects.requireNonNull(coverageService, "coverageService");
        this.requirementCalculator =
                Objects.requireNonNull(requirementCalculator, "requirementCalculator");
        this.materialReferenceResolver =
                Objects.requireNonNull(materialReferenceResolver, "materialReferenceResolver");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Legacy single-order prepare: maps selected items to selections without explicit product
     * quantities (STANDARD resolves full requestable; FLEXIBLE rejects without quantity).
     */
    public MaterialRequirement prepareMaterialRequirement(
            SourceOrderId orderId, List<SourceOrderItemId> selectedOrderItemIds) {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(selectedOrderItemIds, "selectedOrderItemIds");
        List<MaterialRequirementProductSelection> selections = new ArrayList<>();
        Set<SourceOrderItemId> seen = new LinkedHashSet<>();
        for (SourceOrderItemId itemId : selectedOrderItemIds) {
            Objects.requireNonNull(itemId, "selectedOrderItemId");
            if (!seen.add(itemId)) {
                continue;
            }
            selections.add(MaterialRequirementProductSelection.of(orderId, itemId));
        }
        return prepareMaterialRequirement(selections);
    }

    /**
     * Creates and persists a DRAFT Material Requirement from cross-order product selections and
     * frozen Specifications. Product quantities are resolved from Quantity Mode at prepare time and
     * frozen into the DRAFT; later mode changes do not reinterpret the DRAFT.
     */
    public MaterialRequirement prepareMaterialRequirement(
            List<MaterialRequirementProductSelection> selections) {
        Objects.requireNonNull(selections, "selections");
        if (selections.isEmpty()) {
            throw new MaterialRequirementSelectionException(
                    "Material requirement selection must not be empty");
        }

        validateDestinationWarehouse();

        Map<MaterialRequirementSourceItemKey, MaterialRequirementProductSelection> unique =
                new LinkedHashMap<>();
        for (MaterialRequirementProductSelection selection : selections) {
            Objects.requireNonNull(selection, "selection");
            MaterialRequirementSourceItemKey key =
                    MaterialRequirementSourceItemKey.of(
                            selection.sourceOrderId(), selection.sourceOrderItemId());
            if (unique.putIfAbsent(key, selection) != null) {
                throw new MaterialRequirementSelectionException(
                        "Duplicate material requirement selection for " + key);
            }
        }

        Map<SourceOrderId, ProductionQuantityMode> modesByOrder = new LinkedHashMap<>();
        Map<SourceOrderId, Map<SourceOrderItemId, ProductionItemState>> statesByOrder =
                new LinkedHashMap<>();
        for (SourceOrderId orderId :
                unique.keySet().stream()
                        .map(MaterialRequirementSourceItemKey::sourceOrderId)
                        .distinct()
                        .sorted((a, b) -> a.value().compareTo(b.value()))
                        .toList()) {
            OrderProductionView view = orderViewService.getOrderProductionView(orderId);
            if (view.status() != OrderProductionViewStatus.IN_PRODUCTION) {
                throw new MaterialRequirementNotAllowedException(orderId, view.status());
            }
            modesByOrder.put(orderId, resolveMode(orderId));
            Map<SourceOrderItemId, ProductionItemState> byItem = new LinkedHashMap<>();
            for (ProductionItemState state : orderViewService.listItemStates(orderId)) {
                byItem.put(state.sourceOrderItemId(), state);
            }
            statesByOrder.put(orderId, byItem);
        }

        Map<MaterialRequirementSourceItemKey, MaterialRequirementProductCoverageCalculator.ProductItemCoverage>
                coverageByKey = coverageService.coverageForItems(unique.keySet());

        List<MaterialRequirementSourceItem> sourceItems = new ArrayList<>();
        List<SpecificationMaterialRequirementCalculator.ScaledMaterialInput> scaledInputs =
                new ArrayList<>();

        for (MaterialRequirementProductSelection selection : unique.values()) {
            SourceOrderId orderId = selection.sourceOrderId();
            SourceOrderItemId itemId = selection.sourceOrderItemId();
            ProductionItemState state = statesByOrder.get(orderId).get(itemId);
            if (state == null) {
                throw new MaterialRequirementSelectionException(
                        "Selected order item does not belong to order " + orderId + ": " + itemId);
            }
            if (state.status() != ProductionStatus.IN_PRODUCTION
                    && state.status() != ProductionStatus.PARTIALLY_RELEASED) {
                throw new MaterialRequirementSelectionException(
                        "Selected order item is not eligible for material requirement: "
                                + itemId
                                + ", status="
                                + state.status());
            }

            MaterialRequirementSourceItemKey key =
                    MaterialRequirementSourceItemKey.of(orderId, itemId);
            long requestable =
                    coverageByKey
                            .get(key)
                            .requestableProductQuantity();
            ProductionQuantityMode mode = modesByOrder.get(orderId);
            long productQuantity = resolveProductQuantity(selection, mode, requestable, key);

            sourceItems.add(MaterialRequirementSourceItem.of(orderId, itemId, productQuantity));
            List<ResolvedMaterialLine> materialLines = foundationQuery.materialLines(state);
            scaledInputs.add(
                    new SpecificationMaterialRequirementCalculator.ScaledMaterialInput(
                            orderId, itemId, productQuantity, materialLines));
        }

        List<SpecificationMaterialRequirementCalculator.ScaledAggregate> aggregates =
                requirementCalculator.aggregateScaled(scaledInputs);

        List<MaterialRequirementLine> lines = new ArrayList<>();
        for (SpecificationMaterialRequirementCalculator.ScaledAggregate aggregate : aggregates) {
            if (aggregate.requiredQuantity().signum() <= 0) {
                continue;
            }
            List<MaterialReferenceEntry> candidates =
                    warehouseReferences.findMaterialReferencesByIdentity(
                            aggregate.identity().materialCode(),
                            aggregate.identity().color(),
                            aggregate.identity().unitOfMeasure());
            List<MaterialReferenceResolver.CatalogEntry> catalog =
                    candidates.stream().map(this::toCatalogEntry).toList();
            MaterialReferenceResolver.Result resolution =
                    materialReferenceResolver.resolve(aggregate.identity(), catalog);
            SourceOrderId failureOrder = sourceItems.getFirst().sourceOrderId();
            if (resolution.status() == MaterialReferenceResolver.ResolutionStatus.UNRESOLVED) {
                throw new MaterialRequirementNotReadyException(
                        failureOrder,
                        aggregate.identity(),
                        MaterialRequirementNotReadyException.Problem.UNRESOLVED);
            }
            if (resolution.status() == MaterialReferenceResolver.ResolutionStatus.AMBIGUOUS) {
                throw new MaterialRequirementNotReadyException(
                        failureOrder,
                        aggregate.identity(),
                        MaterialRequirementNotReadyException.Problem.AMBIGUOUS);
            }

            MaterialReferenceEntry matched =
                    candidates.stream()
                            .filter(
                                    entry ->
                                            entry.materialReferenceId()
                                                    .equals(resolution.materialReferenceId()))
                            .findFirst()
                            .orElse(null);
            String materialName =
                    matched != null ? matched.name() : aggregate.materialName();

            lines.add(
                    MaterialRequirementLine.create(
                            MaterialReferenceId.of(resolution.materialReferenceId()),
                            aggregate.identity().materialCode(),
                            materialName,
                            aggregate.identity().color(),
                            aggregate.identity().unitOfMeasure(),
                            aggregate.requiredQuantity(),
                            aggregate.contributions()));
        }

        Instant now = clock.instant();
        MaterialRequirement requirement =
                MaterialRequirement.create(
                        destinationWarehouse.productionWarehouseId(),
                        now,
                        sourceItems,
                        lines);
        return requirementRepository.save(requirement);
    }

    public Optional<MaterialRequirement> findById(MaterialRequirementId id) {
        Objects.requireNonNull(id, "id");
        return requirementRepository.findById(id);
    }

    public List<MaterialRequirement> findBySourceOrderItemIds(List<SourceOrderItemId> itemIds) {
        Objects.requireNonNull(itemIds, "itemIds");
        return requirementRepository.findBySourceOrderItemIds(itemIds);
    }

    /**
     * Changes line quantity with caller {@code expectedVersion} participating in the same load →
     * compare → mutate → optimistic save flow (no separate facade pre-check).
     */
    public MaterialRequirement changeQuantity(
            MaterialRequirementId id,
            MaterialRequirementLineId lineId,
            BigDecimal quantity,
            long expectedVersion) {
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
        if (requirement.version() != expectedVersion) {
            throw new MaterialRequirementOptimisticLockException(id, expectedVersion);
        }
        MaterialRequirement edited =
                requirement.changeLineQuantity(lineId, quantity, clock.instant());
        return requirementRepository.save(edited);
    }

    private long resolveProductQuantity(
            MaterialRequirementProductSelection selection,
            ProductionQuantityMode mode,
            long requestable,
            MaterialRequirementSourceItemKey key) {
        if (requestable <= 0L) {
            throw new MaterialRequirementSelectionException(
                    "No requestable product quantity for " + key);
        }
        return switch (mode) {
            case STANDARD -> {
                if (selection.requestedProductQuantity().isPresent()) {
                    throw new MaterialRequirementSelectionException(
                            "STANDARD mode must not supply requestedProductQuantity for " + key);
                }
                yield requestable;
            }
            case FLEXIBLE -> {
                if (selection.requestedProductQuantity().isEmpty()) {
                    throw new MaterialRequirementSelectionException(
                            "FLEXIBLE mode requires requestedProductQuantity for " + key);
                }
                long requested = selection.requestedProductQuantity().getAsLong();
                if (requested <= 0L) {
                    throw new MaterialRequirementSelectionException(
                            "requestedProductQuantity must be > 0 for " + key + ": " + requested);
                }
                if (requested > requestable) {
                    throw new MaterialRequirementSelectionException(
                            "requestedProductQuantity "
                                    + requested
                                    + " exceeds requestable "
                                    + requestable
                                    + " for "
                                    + key);
                }
                yield requested;
            }
        };
    }

    private ProductionQuantityMode resolveMode(SourceOrderId orderId) {
        return quantityModeRepository
                .findBySourceOrderId(orderId)
                .map(OrderQuantityModeSetting::quantityMode)
                .orElse(ProductionQuantityMode.defaultMode());
    }

    private void validateDestinationWarehouse() {
        UUID warehouseId = destinationWarehouse.productionWarehouseId();
        WarehouseReferenceEntry entry =
                warehouseReferences
                        .getWarehouse(warehouseId)
                        .orElseThrow(
                                () ->
                                        InvalidProductionDestinationWarehouseException
                                                .warehouseNotFound(warehouseId));
        if (!entry.active()) {
            throw InvalidProductionDestinationWarehouseException.warehouseInactive(warehouseId);
        }
    }

    private MaterialReferenceResolver.CatalogEntry toCatalogEntry(MaterialReferenceEntry entry) {
        return new MaterialReferenceResolver.CatalogEntry(
                entry.materialReferenceId(),
                entry.article(),
                entry.color(),
                entry.unitOfMeasure());
    }
}
