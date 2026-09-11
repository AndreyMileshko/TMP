package com.tmp.warehouse.api;

import com.tmp.warehouse.api.WarehouseApi.AvailabilityResult;
import com.tmp.warehouse.api.WarehouseApi.MaterialDemand;
import com.tmp.warehouse.api.WarehouseApi.MaterialIdentityRequest;
import com.tmp.warehouse.api.WarehouseApi.MaterialReferenceDisplayView;
import com.tmp.warehouse.api.WarehouseApi.MaterialReferenceView;
import com.tmp.warehouse.api.WarehouseApi.MaterialSourceRoutingResult;
import com.tmp.warehouse.api.WarehouseApi.ReservationLinkView;
import com.tmp.warehouse.api.WarehouseApi.StockView;
import com.tmp.warehouse.api.WarehouseApi.StorageCellView;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentReturnPlanItem;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentSourceSuggestionLine;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentView;
import com.tmp.warehouse.api.WarehouseApi.TransferRequestView;
import com.tmp.warehouse.api.WarehouseApi.TransferStatusView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseView;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Warehouse Public Query API — read-only inter-capability contract (Specification §17.1).
 *
 * <p>Does not mutate Stock Position, Warehouse Movement or Warehouse Operations.
 */
public interface WarehouseQueryApi {

    List<WarehouseView> listWarehouses();

    /**
     * Active warehouses for which the current authenticated user is responsible (ADR-037).
     * Does not change {@link #listWarehouses()} global catalogue semantics.
     */
    List<WarehouseView> listMyWarehouses();

    /** Responsible Security user ids for a warehouse (opaque UUIDs). */
    List<UUID> listResponsibleUserIds(UUID warehouseId);

    List<StorageCellView> listStorageCells(UUID warehouseId);

    List<MaterialReferenceView> listMaterialReferences();

    List<String> listUnitOfMeasures();

    List<StockView> getStock(String materialCode);

    List<StockView> getStock(String materialCode, UUID warehouseId, UUID storageCellId);

    List<StockView> getStockByWarehouse(UUID warehouseId);

    List<StockView> getStockByMaterialReferenceId(UUID materialReferenceId);

    MaterialReferenceDisplayView getMaterialReferenceDisplay(String materialCode);

    /**
     * Exact material identity availability across all warehouses (AVAILABLE stock only).
     */
    AvailabilityResult checkAvailability(MaterialIdentityRequest identity, BigDecimal quantity);

    /**
     * Exact material identity availability scoped to one warehouse.
     */
    AvailabilityResult checkAvailability(
            MaterialIdentityRequest identity, UUID warehouseId, BigDecimal quantity);

    /** Availability by stable Warehouse-owned material reference id. */
    AvailabilityResult checkAvailability(UUID materialReferenceId, BigDecimal quantity);

    /** Availability scoped to warehouse by material reference id. */
    AvailabilityResult checkAvailability(
            UUID materialReferenceId, UUID warehouseId, BigDecimal quantity);

    /**
     * Legacy article-only lookup (empty color/size/unit). Only valid for legacy migrated materials.
     */
    AvailabilityResult checkAvailabilityByLegacyArticle(String materialCode, BigDecimal quantity);

    List<ReservationLinkView> listReservationLinks(String materialCode);

    TransferStatusView getTransferStatus(UUID operationId);

    /** Lists Warehouse-owned transfer requests still in {@code DRAFT} (no stock movement yet). */
    List<TransferRequestView> listTransferDrafts();

    /**
     * Reads a Warehouse-owned multi-line Transfer Document (Document Engine metadata + typed
     * payload). Does not mutate stock.
     */
    TransferDocumentView getTransferDocument(UUID documentId);

    /**
     * Suggests source-cell allocations for a Transfer Document whose source warehouse is already
     * fixed. Uses the same cell FIFO selection as automatic routing, scoped to {@code
     * document.sourceWarehouseId} only. Planning/query only — does not mutate stock.
     */
    List<TransferDocumentSourceSuggestionLine> suggestTransferDocumentSourceAllocations(
            UUID documentId);

    /**
     * Read-only default return plan for a RETURN_PENDING Transfer Document: one item per
     * outstanding send allocation (same {@code lineId} may appear more than once). Empty when
     * nothing is outstanding. Does not mutate stock.
     */
    List<TransferDocumentReturnPlanItem> listTransferDocumentReturnPlan(UUID documentId);

    /**
     * Batch automatic source warehouse routing + source cell suggestions (ADR-037 / Stage 3.5.4).
     *
     * <p>Planning/query only: does not mutate stock, create reservations, operations, movements, or
     * transfer documents. Destination warehouse is excluded from candidates. Uses AVAILABLE stock
     * on active cells of active warehouses only. Results preserve input demand order via {@code
     * demandKey}. Does not apply {@code listMyWarehouses} / responsibility filters. Caller owns
     * user-facing authorization.
     */
    List<MaterialSourceRoutingResult> routeMaterials(
            UUID destinationWarehouseId, List<MaterialDemand> demands);

    /**
     * Single-material convenience wrapper over {@link #routeMaterials}. Uses demand key {@code
     * "1"}.
     */
    default MaterialSourceRoutingResult routeMaterial(
            UUID destinationWarehouseId, UUID materialReferenceId, BigDecimal quantity) {
        return routeMaterials(
                        destinationWarehouseId,
                        List.of(new MaterialDemand("1", materialReferenceId, quantity)))
                .get(0);
    }

    /**
     * Operational inbox: TRANSFER_PREPARATION (DRAFT / source), TRANSFER_RECEIPT (POSTED +
     * AWAITING_RECEIPT / destination), RETURN_MATERIALS (POSTED + RETURN_PENDING / source).
     *
     * <p>{@code warehouseId} null = all responsible warehouses; non-null must be in that scope
     * (otherwise access denied). Does not mutate stock. Ordering: NEW then IN_WORK, oldest {@code
     * createdAt}, then {@code documentId}.
     */
    List<WarehouseTaskView> listMyWarehouseTasks(UUID warehouseId);

    /**
     * Modern Остатки summary page: one row per material (per warehouse when filter is a single
     * warehouse or «Все мои склады»). Quantity is AVAILABLE only. Server-side search and
     * pagination. Does not mutate stock.
     *
     * <p>{@code warehouseId} null = all responsible warehouses; non-null must be in responsibility
     * scope (otherwise access denied).
     */
    default WarehouseApi.WarehouseStockPage listStockSummaries(
            UUID warehouseId, String search, int pageIndex, int pageSize) {
        throw new UnsupportedOperationException("listStockSummaries is not available");
    }

    /**
     * Cell-level AVAILABLE breakdown for one material on one warehouse. Loaded on expand. Does not
     * mutate stock. Foreign/non-responsible warehouse is access denied.
     */
    default WarehouseApi.WarehouseMaterialStockDetailsView getStockCellBreakdown(
            UUID warehouseId, UUID materialReferenceId) {
        throw new UnsupportedOperationException("getStockCellBreakdown is not available");
    }

    /**
     * Modern Остатки cell-centric page: one row per warehouse + storage cell + material with
     * positive AVAILABLE. Server-side search, cell filter, and pagination. Does not mutate stock.
     *
     * <p>{@code warehouseId} null = all responsible warehouses. {@code storageCellId} null = all
     * cells in scope. Foreign warehouse or foreign cell is access denied.
     */
    default WarehouseApi.WarehouseStockCellPage listStockByCells(
            UUID warehouseId,
            UUID storageCellId,
            String search,
            int pageIndex,
            int pageSize) {
        throw new UnsupportedOperationException("listStockByCells is not available");
    }

    /**
     * Active storage cells for Stocks cell filter within responsibility scope. {@code warehouseId}
     * null = all responsible warehouses. Does not mutate stock.
     */
    default List<WarehouseApi.WarehouseStockCellFilterOptionView> listStockCellFilterOptions(
            UUID warehouseId) {
        throw new UnsupportedOperationException("listStockCellFilterOptions is not available");
    }

    /**
     * Modern Warehouse History page: completed physical operations for responsible warehouse(s).
     * Server-side filters and pagination. Does not mutate stock.
     *
     * <p>{@code warehouseId} null = all responsible warehouses; non-null must be in responsibility
     * scope (otherwise access denied).
     */
    default WarehouseApi.WarehouseHistoryPage listHistory(
            UUID warehouseId,
            WarehouseApi.WarehouseHistoryFilter filter,
            int pageIndex,
            int pageSize) {
        throw new UnsupportedOperationException("listHistory is not available");
    }
}
