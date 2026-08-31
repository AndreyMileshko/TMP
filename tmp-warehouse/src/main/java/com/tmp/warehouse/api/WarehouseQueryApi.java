package com.tmp.warehouse.api;

import com.tmp.warehouse.api.WarehouseApi.AvailabilityResult;
import com.tmp.warehouse.api.WarehouseApi.MaterialIdentityRequest;
import com.tmp.warehouse.api.WarehouseApi.MaterialReferenceDisplayView;
import com.tmp.warehouse.api.WarehouseApi.MaterialReferenceView;
import com.tmp.warehouse.api.WarehouseApi.ReservationLinkView;
import com.tmp.warehouse.api.WarehouseApi.StockView;
import com.tmp.warehouse.api.WarehouseApi.StorageCellView;
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
}
