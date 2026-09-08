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
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentView;
import com.tmp.warehouse.api.WarehouseApi.TransferRequestView;
import com.tmp.warehouse.api.WarehouseApi.TransferStatusView;
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
}
