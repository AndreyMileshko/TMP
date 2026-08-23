package com.tmp.production.application;

import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentStatus;
import com.tmp.production.application.ReleaseMaterialPlanBuilder.PlannedMaterialLine;
import com.tmp.production.application.ReleaseProductsCommand.CellAllocation;
import com.tmp.production.application.ReleaseProductsCommand.ItemRelease;
import com.tmp.production.application.ReleaseProductsCommand.MaterialActualUsage;
import com.tmp.production.application.ReleaseProductsResult.ConsumptionReference;
import com.tmp.production.application.ReleaseProductsResult.ItemResult;
import com.tmp.production.application.ReleaseProductsResult.MaterialResult;
import com.tmp.production.application.ReleaseProductsResult.PrepareReleasePreview;
import com.tmp.production.application.internal.ProductionReleaseDocumentService;
import com.tmp.production.application.internal.ProductionReleaseDocumentService.ProductionReleaseDocumentCommand;
import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedMaterialLine;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort.MaterialReferenceEntry;
import com.tmp.production.domain.FrozenSpecificationUnavailableException;
import com.tmp.production.domain.OrderProductionViewCalculator;
import com.tmp.production.domain.OrderProductionViewCalculator.Context;
import com.tmp.production.domain.OrderProductionViewStatus;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionStatus;
import com.tmp.production.domain.ReleaseProductsException;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import com.tmp.warehouse.api.WarehouseApi.ConsumptionCommand;
import com.tmp.warehouse.api.WarehouseApi.StockStateView;
import com.tmp.warehouse.api.WarehouseApi.StockView;
import com.tmp.warehouse.api.WarehouseApi.OperationKind;
import com.tmp.warehouse.api.WarehouseApi.OperationResult;
import com.tmp.warehouse.api.WarehouseApi.StorageCellView;
import com.tmp.warehouse.api.WarehouseCommandApi;
import com.tmp.warehouse.api.WarehouseQueryApi;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Application orchestration for «Выпустить изделия»: system-computed plan, confirmed actual usage,
 * Warehouse Consumption and Production Release POST in one outer ACID transaction (ADR-036).
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores injected collaborators, Warehouse public APIs, TX manager and clock.")
public final class ReleaseProductsService {

    private static final String STATUS_COMPLETED = "COMPLETED";

    private final ProductionFoundationQueryService foundationQuery;
    private final WarehouseAvailabilityQueryPort warehouseAvailabilityQuery;
    private final WarehouseCommandApi warehouseCommandApi;
    private final WarehouseQueryApi warehouseQueryApi;
    private final ProductionWarehouseScope warehouseScope;
    private final ProductionReleaseDocumentService releaseDocumentService;
    private final ProductionOrderStateLockService stateLockService;
    private final ReleaseMaterialPlanBuilder planBuilder;
    private final OrderProductionViewCalculator viewCalculator;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public ReleaseProductsService(
            ProductionOrderViewService orderViewService,
            ProductionFoundationQueryService foundationQuery,
            WarehouseAvailabilityQueryPort warehouseAvailabilityQuery,
            WarehouseCommandApi warehouseCommandApi,
            WarehouseQueryApi warehouseQueryApi,
            ProductionWarehouseScope warehouseScope,
            ProductionReleaseDocumentService releaseDocumentService,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this(
                foundationQuery,
                warehouseAvailabilityQuery,
                warehouseCommandApi,
                warehouseQueryApi,
                warehouseScope,
                releaseDocumentService,
                new ProductionOrderStateLockService(orderViewService),
                new ReleaseMaterialPlanBuilder(),
                new OrderProductionViewCalculator(),
                transactionManager,
                clock);
    }

    ReleaseProductsService(
            ProductionFoundationQueryService foundationQuery,
            WarehouseAvailabilityQueryPort warehouseAvailabilityQuery,
            WarehouseCommandApi warehouseCommandApi,
            WarehouseQueryApi warehouseQueryApi,
            ProductionWarehouseScope warehouseScope,
            ProductionReleaseDocumentService releaseDocumentService,
            ProductionOrderStateLockService stateLockService,
            ReleaseMaterialPlanBuilder planBuilder,
            OrderProductionViewCalculator viewCalculator,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.foundationQuery = Objects.requireNonNull(foundationQuery, "foundationQuery");
        this.warehouseAvailabilityQuery =
                Objects.requireNonNull(warehouseAvailabilityQuery, "warehouseAvailabilityQuery");
        this.warehouseCommandApi =
                Objects.requireNonNull(warehouseCommandApi, "warehouseCommandApi");
        this.warehouseQueryApi = Objects.requireNonNull(warehouseQueryApi, "warehouseQueryApi");
        this.warehouseScope = Objects.requireNonNull(warehouseScope, "warehouseScope");
        this.releaseDocumentService =
                Objects.requireNonNull(releaseDocumentService, "releaseDocumentService");
        this.stateLockService = Objects.requireNonNull(stateLockService, "stateLockService");
        this.planBuilder = Objects.requireNonNull(planBuilder, "planBuilder");
        this.viewCalculator = Objects.requireNonNull(viewCalculator, "viewCalculator");
        this.transactionTemplate =
                new TransactionTemplate(
                        Objects.requireNonNull(transactionManager, "transactionManager"));
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Informational preview: system-computed plan with default actual = planned. No Warehouse or
     * Production mutations; does not require confirmed actual usage or cell allocations.
     */
    public PrepareReleasePreview prepareRelease(PrepareReleaseCommand command) {
        Objects.requireNonNull(command, "command");
        SourceOrderId sourceOrderId = SourceOrderId.of(command.sourceOrderId());
        List<ProductionItemState> states = stateLockService.readAllItemStates(sourceOrderId);
        requireInProduction(sourceOrderId, states);
        rejectDuplicateItems(command.itemReleases());
        PlanSnapshot snapshot = computePlanSnapshot(command.itemReleases(), states);
        List<MaterialResult> defaultActuals =
                snapshot.plannedLines().stream()
                        .map(
                                line ->
                                        new MaterialResult(
                                                line.sourceOrderItemId().value(),
                                                line.materialReferenceId().value(),
                                                line.plannedQuantity(),
                                                line.plannedQuantity()))
                        .toList();
        return new PrepareReleasePreview(
                command.sourceOrderId(),
                snapshot.itemResults(),
                snapshot.plannedLines(),
                defaultActuals);
    }

    /**
     * Confirms Release: plan is always recomputed inside the outer transaction on locked state.
     * Does not trust any prior {@link #prepareRelease} snapshot.
     */
    public ReleaseProductsResult releaseProducts(ReleaseProductsCommand command) {
        Objects.requireNonNull(command, "command");
        ReleaseProductsResult result =
                transactionTemplate.execute(
                        status -> {
                            SourceOrderId sourceOrderId =
                                    SourceOrderId.of(command.sourceOrderId());
                            List<ProductionItemState> lockedStates =
                                    stateLockService.lockAllItemStates(sourceOrderId);
                            requireInProduction(sourceOrderId, lockedStates);
                            rejectDuplicateItems(command.itemReleases());
                            Map<UUID, StorageCellView> productionCells =
                                    indexActiveProductionCells(
                                            warehouseScope.productionWarehouseId());
                            PreparedRelease prepared =
                                    prepareConfirmRelease(
                                            command, lockedStates, productionCells);
                            return executeRelease(command, prepared);
                        });
        if (result == null) {
            throw new ReleaseProductsException(
                    "Release orchestration returned null for order " + command.sourceOrderId());
        }
        return result;
    }

    private ReleaseProductsResult executeRelease(
            ReleaseProductsCommand command, PreparedRelease prepared) {
        Instant releasedAt = clock.instant();
        DocumentMetadata draft =
                releaseDocumentService.createDraft(
                        toDocumentCommand(command.sourceOrderId(), releasedAt, prepared));

        List<ConsumptionReference> consumptionReferences = new ArrayList<>();
        UUID productionWarehouseId = warehouseScope.productionWarehouseId();
        for (MaterialActualUsage usage : command.materialActualUsages()) {
            if (usage.actualQuantity().signum() == 0) {
                continue;
            }
            for (CellAllocation allocation : usage.allocations()) {
                OperationResult consumed =
                        warehouseCommandApi.consume(
                                new ConsumptionCommand(
                                        usage.materialReferenceId(),
                                        allocation.quantity(),
                                        productionWarehouseId,
                                        allocation.storageCellId()));
                validateConsumptionResult(
                        usage.materialReferenceId(),
                        productionWarehouseId,
                        allocation,
                        consumed);
                consumptionReferences.add(
                        new ConsumptionReference(
                                consumed.operationId(),
                                usage.materialReferenceId(),
                                productionWarehouseId,
                                allocation.storageCellId(),
                                allocation.quantity()));
            }
        }

        DocumentMetadata posted = releaseDocumentService.post(draft.id());
        if (posted.status() != DocumentStatus.POSTED) {
            throw new ReleaseProductsException(
                    "Production Release post did not reach POSTED status: " + posted.status());
        }

        return new ReleaseProductsResult(
                draft.id(),
                command.sourceOrderId(),
                releasedAt,
                prepared.itemResults(),
                prepared.materialResults(),
                consumptionReferences);
    }

    private PreparedRelease prepareConfirmRelease(
            ReleaseProductsCommand command,
            List<ProductionItemState> lockedStates,
            Map<UUID, StorageCellView> productionCells) {
        PlanSnapshot snapshot = computePlanSnapshot(command.itemReleases(), lockedStates);
        Map<String, PlannedMaterialLine> plannedByKey = indexPlannedLines(snapshot.plannedLines());
        Map<String, MaterialActualUsage> actualByKey = indexActualUsages(command, plannedByKey);

        List<MaterialResult> materialResults = new ArrayList<>(plannedByKey.size());
        for (PlannedMaterialLine planned : plannedByKey.values()) {
            MaterialActualUsage actual = actualByKey.get(planned.stableKey());
            if (actual == null) {
                throw new ReleaseProductsException(
                        "Missing confirmed actual usage for planned line: " + planned.stableKey());
            }
            validateActualAgainstPlan(planned, actual);
            validateAllocations(actual, productionCells);
            precheckStock(actual);
            materialResults.add(
                    new MaterialResult(
                            planned.sourceOrderItemId().value(),
                            planned.materialReferenceId().value(),
                            planned.plannedQuantity(),
                            actual.actualQuantity()));
        }

        return new PreparedRelease(
                snapshot.itemResults(),
                snapshot.plannedLines(),
                List.copyOf(materialResults),
                snapshot.documentItemLines());
    }

    private PlanSnapshot computePlanSnapshot(
            List<ItemRelease> itemReleases, List<ProductionItemState> states) {
        List<MaterialReferenceEntry> materialCatalog =
                warehouseAvailabilityQuery.listMaterialReferences();
        Map<SourceOrderItemId, ProductionItemState> stateByItem = indexStatesByItem(states);

        Map<String, PlannedMaterialLine> plannedByKey = new LinkedHashMap<>();
        List<ItemResult> itemResults = new ArrayList<>();
        List<ProductionReleaseDocumentCommand.ItemLine> documentItemLines = new ArrayList<>();

        for (ItemRelease itemRelease : itemReleases) {
            SourceOrderItemId itemId = SourceOrderItemId.of(itemRelease.sourceOrderItemId());
            ProductionItemState state =
                    requireReleasableItem(stateByItem, itemId, itemRelease.releaseQuantity());
            List<ResolvedMaterialLine> specLines = foundationQuery.materialLines(state);
            List<PlannedMaterialLine> itemPlans =
                    planBuilder.buildPlannedLines(
                            state,
                            itemRelease.releaseQuantity(),
                            specLines,
                            materialCatalog);
            for (PlannedMaterialLine planned : itemPlans) {
                plannedByKey.put(planned.stableKey(), planned);
            }
            itemResults.add(new ItemResult(itemId.value(), itemRelease.releaseQuantity()));
            documentItemLines.add(
                    new ProductionReleaseDocumentCommand.ItemLine(
                            itemId.value(),
                            state.specificationId().value(),
                            BigDecimal.valueOf(itemRelease.releaseQuantity())));
        }

        return new PlanSnapshot(
                List.copyOf(itemResults),
                List.copyOf(plannedByKey.values()),
                List.copyOf(documentItemLines));
    }

    private void requireInProduction(SourceOrderId sourceOrderId, List<ProductionItemState> states) {
        if (viewCalculator.calculate(sourceOrderId, states, Context.none()).status()
                != OrderProductionViewStatus.IN_PRODUCTION) {
            throw new ReleaseProductsException(
                    "Release is allowed only when order Production View is IN_PRODUCTION");
        }
    }

    private static Map<SourceOrderItemId, ProductionItemState> indexStatesByItem(
            List<ProductionItemState> states) {
        Map<SourceOrderItemId, ProductionItemState> byItem = new HashMap<>();
        for (ProductionItemState state : states) {
            ProductionItemState previous = byItem.put(state.sourceOrderItemId(), state);
            if (previous != null) {
                throw new ReleaseProductsException(
                        "Ambiguous Production item state for item "
                                + state.sourceOrderItemId().value());
            }
        }
        return byItem;
    }

    private ProductionItemState requireReleasableItem(
            Map<SourceOrderItemId, ProductionItemState> stateByItem,
            SourceOrderItemId itemId,
            long releaseQuantity) {
        ProductionItemState state = stateByItem.get(itemId);
        if (state == null) {
            throw new ReleaseProductsException(
                    "Production item state not found: " + itemId.value());
        }
        if (state.status() != ProductionStatus.IN_PRODUCTION
                && state.status() != ProductionStatus.PARTIALLY_RELEASED) {
            throw new ReleaseProductsException(
                    "Release rejected for item status " + state.status() + ": " + itemId.value());
        }
        if (state.activeProductionQuantity().value().longValueExact() < releaseQuantity) {
            throw new ReleaseProductsException(
                    "Release quantity exceeds active production quantity for item "
                            + itemId.value());
        }
        try {
            foundationQuery.materialLines(state);
        } catch (FrozenSpecificationUnavailableException ex) {
            throw new ReleaseProductsException(
                    "Frozen specification unavailable for item " + itemId.value(), ex);
        }
        return state;
    }

    private static Map<String, PlannedMaterialLine> indexPlannedLines(
            List<PlannedMaterialLine> plannedLines) {
        Map<String, PlannedMaterialLine> plannedByKey = new LinkedHashMap<>();
        for (PlannedMaterialLine planned : plannedLines) {
            plannedByKey.put(planned.stableKey(), planned);
        }
        return plannedByKey;
    }

    private static void rejectDuplicateItems(List<ItemRelease> itemReleases) {
        Set<UUID> seen = new HashSet<>();
        for (ItemRelease release : itemReleases) {
            if (!seen.add(release.sourceOrderItemId())) {
                throw new ReleaseProductsException(
                        "Duplicate sourceOrderItemId in release request: "
                                + release.sourceOrderItemId());
            }
        }
    }

    private static Map<String, MaterialActualUsage> indexActualUsages(
            ReleaseProductsCommand command, Map<String, PlannedMaterialLine> plannedByKey) {
        Map<String, MaterialActualUsage> actualByKey = new HashMap<>();
        for (MaterialActualUsage usage : command.materialActualUsages()) {
            String key = usage.stableKey();
            if (!plannedByKey.containsKey(key)) {
                throw new ReleaseProductsException(
                        "Extra material not in system-calculated plan: " + key);
            }
            if (actualByKey.put(key, usage) != null) {
                throw new ReleaseProductsException("Duplicate actual usage key: " + key);
            }
        }
        return actualByKey;
    }

    private static void validateActualAgainstPlan(
            PlannedMaterialLine planned, MaterialActualUsage actual) {
        if (!planned.sourceOrderItemId().value().equals(actual.sourceOrderItemId())
                || !planned.materialReferenceId().value().equals(actual.materialReferenceId())) {
            throw new ReleaseProductsException(
                    "Actual usage key mismatch for planned line " + planned.stableKey());
        }
    }

    private void validateAllocations(
            MaterialActualUsage actual, Map<UUID, StorageCellView> productionCells) {
        if (actual.actualQuantity().signum() == 0) {
            if (!actual.allocations().isEmpty()) {
                throw new ReleaseProductsException(
                        "Zero actual requires empty allocations for "
                                + actual.materialReferenceId());
            }
            return;
        }
        BigDecimal allocationTotal = BigDecimal.ZERO;
        Set<UUID> seenCells = new HashSet<>();
        for (CellAllocation allocation : actual.allocations()) {
            if (!seenCells.add(allocation.storageCellId())) {
                throw new ReleaseProductsException(
                        "Duplicate storage cell allocation: " + allocation.storageCellId());
            }
            requireProductionCell(productionCells, allocation.storageCellId());
            allocationTotal = allocationTotal.add(allocation.quantity());
        }
        if (allocationTotal.compareTo(actual.actualQuantity()) != 0) {
            throw new ReleaseProductsException(
                    "Allocation total must equal actualQuantity for material "
                            + actual.materialReferenceId()
                            + ": allocations="
                            + allocationTotal
                            + ", actual="
                            + actual.actualQuantity());
        }
    }

    private void precheckStock(MaterialActualUsage actual) {
        UUID productionWarehouseId = warehouseScope.productionWarehouseId();
        List<StockView> stockViews =
                warehouseQueryApi.getStockByMaterialReferenceId(actual.materialReferenceId());
        for (CellAllocation allocation : actual.allocations()) {
            BigDecimal available =
                    stockViews.stream()
                            .filter(
                                    view ->
                                            view.stockState() == StockStateView.AVAILABLE
                                                    && productionWarehouseId.equals(
                                                            view.warehouseId())
                                                    && allocation
                                                            .storageCellId()
                                                            .equals(view.storageCellId()))
                            .map(StockView::quantity)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (available.compareTo(allocation.quantity()) < 0) {
                throw new ReleaseProductsException(
                        "Insufficient production warehouse stock for material "
                                + actual.materialReferenceId()
                                + " in cell "
                                + allocation.storageCellId()
                                + ": available="
                                + available
                                + ", required="
                                + allocation.quantity());
            }
        }
    }

    private Map<UUID, StorageCellView> indexActiveProductionCells(UUID productionWarehouseId) {
        Map<UUID, StorageCellView> cells = new HashMap<>();
        for (StorageCellView cell : warehouseQueryApi.listStorageCells(productionWarehouseId)) {
            if (!cell.active()) {
                continue;
            }
            if (!cell.warehouseId().equals(productionWarehouseId)) {
                throw new ReleaseProductsException(
                        "Storage cell "
                                + cell.storageCellId()
                                + " does not belong to production warehouse");
            }
            cells.put(cell.storageCellId(), cell);
        }
        return cells;
    }

    private static void requireProductionCell(
            Map<UUID, StorageCellView> productionCells, UUID storageCellId) {
        StorageCellView cell = productionCells.get(storageCellId);
        if (cell == null) {
            throw new ReleaseProductsException(
                    "Storage cell not found or inactive in production warehouse: "
                            + storageCellId);
        }
    }

    private static void validateConsumptionResult(
            UUID materialReferenceId,
            UUID productionWarehouseId,
            CellAllocation allocation,
            OperationResult result) {
        if (result == null || result.operationId() == null) {
            throw new ReleaseProductsException("Warehouse consume returned null operationId");
        }
        if (result.kind() != OperationKind.CONSUMPTION) {
            throw new ReleaseProductsException(
                    "Warehouse consume must return CONSUMPTION, got: " + result.kind());
        }
        if (!STATUS_COMPLETED.equals(result.status())) {
            throw new ReleaseProductsException(
                    "Warehouse consume must return COMPLETED status, got: " + result.status());
        }
        if (!materialReferenceId.equals(result.materialReferenceId())) {
            throw new ReleaseProductsException("Warehouse consume materialReferenceId mismatch");
        }
        if (!productionWarehouseId.equals(result.warehouseId())) {
            throw new ReleaseProductsException("Warehouse consume warehouseId mismatch");
        }
        if (!allocation.storageCellId().equals(result.storageCellId())) {
            throw new ReleaseProductsException("Warehouse consume storageCellId mismatch");
        }
        // OperationResult.quantity for CONSUMPTION is the post-operation stock level (Warehouse
        // public contract); confirmed consumption amount is allocation.quantity() from the command.
    }

    private ProductionReleaseDocumentCommand toDocumentCommand(
            UUID sourceOrderId, Instant releasedAt, PreparedRelease prepared) {
        List<ProductionReleaseDocumentCommand.MaterialLine> materialLines = new ArrayList<>();
        Map<String, MaterialResult> resultsByKey = new HashMap<>();
        for (MaterialResult result : prepared.materialResults()) {
            resultsByKey.put(result.sourceOrderItemId() + "|" + result.materialReferenceId(), result);
        }
        for (PlannedMaterialLine planned : prepared.plannedLines()) {
            MaterialResult result = resultsByKey.get(planned.stableKey());
            materialLines.add(
                    new ProductionReleaseDocumentCommand.MaterialLine(
                            planned.materialReferenceId().value(),
                            planned.plannedQuantity(),
                            result.actualQuantity(),
                            planned.planningSource(),
                            planned.cuttingPlanId().orElse(null),
                            planned.sourceOrderItemId().value(),
                            null));
        }
        return new ProductionReleaseDocumentCommand(
                sourceOrderId, releasedAt, prepared.documentItemLines(), materialLines);
    }

    private record PlanSnapshot(
            List<ItemResult> itemResults,
            List<PlannedMaterialLine> plannedLines,
            List<ProductionReleaseDocumentCommand.ItemLine> documentItemLines) {}

    private record PreparedRelease(
            List<ItemResult> itemResults,
            List<PlannedMaterialLine> plannedLines,
            List<MaterialResult> materialResults,
            List<ProductionReleaseDocumentCommand.ItemLine> documentItemLines) {}
}
