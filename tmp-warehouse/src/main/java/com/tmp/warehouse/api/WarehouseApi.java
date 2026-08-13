package com.tmp.warehouse.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Minimal Public API of Warehouse for inter-Capability interaction (Specification §17).
 *
 * <p>Exposes stock reads, availability checks, informational reservation links, and Warehouse
 * Operation execution. Does not expose domain aggregates, persistence entities, Stock Position
 * mutation, or direct Movement creation.
 */
public interface WarehouseApi {

    /**
     * Returns all warehouses (code, name, active).
     *
     * @return warehouse views ordered by code; empty when none
     */
    List<WarehouseView> listWarehouses();

    /**
     * Creates a warehouse in the catalogue.
     *
     * @param command code, name, active flag
     * @return created warehouse view
     */
    WarehouseView createWarehouse(CreateWarehouseCommand command);

    /**
     * Returns storage cells for a warehouse ordered by code.
     */
    List<StorageCellView> listStorageCells(UUID warehouseId);

    /**
     * Creates a storage cell in the catalogue for an existing warehouse.
     */
    StorageCellView createStorageCell(CreateStorageCellCommand command);

    /**
     * Returns warehouse-owned material references for selection in operations.
     */
    List<MaterialReferenceView> listMaterialReferences();

    /**
     * Returns fixed warehouse unit-of-measure codes for Receipt ComboBox selection.
     *
     * <p>Canonical codes only (e.g. {@code шт.}, {@code м.}). No conversion between units.
     */
    List<String> listUnitOfMeasures();

    /**
     * Returns current stock positions for the given material article (any color/size/unit variant).
     *
     * <p>Zero-quantity positions are excluded from the view (including {@code IN_TRANSIT = 0}).
     * Physical Stock Position rows are not deleted.
     *
     * @param materialCode material reference from Specification context
     * @return stock views with {@code quantity > 0}; empty when none
     */
    List<StockView> getStock(String materialCode);

    /**
     * Returns stock for material filtered by warehouse and storage cell (Specification §17).
     *
     * <p>Zero-quantity positions are excluded from the view.
     */
    List<StockView> getStock(String materialCode, UUID warehouseId, UUID storageCellId);

    /**
     * Returns stock positions for a warehouse with {@code quantity > 0}.
     *
     * <p>Zero-quantity positions (any state) are hidden from the Stock View only.
     */
    List<StockView> getStockByWarehouse(UUID warehouseId);

    /**
     * Resolves extended MaterialReference display information for Warehouse UI and operations.
     *
     * <p>Does not create or mutate material master data.
     */
    MaterialReferenceDisplayView getMaterialReferenceDisplay(String materialCode);

    /**
     * Checks whether AVAILABLE stock for the material covers the requested quantity.
     */
    AvailabilityResult checkAvailability(String materialCode, BigDecimal quantity);

    /**
     * Creates an informational reservation link. Does not change Stock Position or Movement.
     */
    ReservationLinkView createReservationLink(CreateReservationLinkCommand command);

    /**
     * Returns informational reservation links for a material reference.
     */
    List<ReservationLinkView> listReservationLinks(String materialCode);

    /**
     * Executes a Warehouse Operation (Receipt / Move / Transfer / Consumption / Adjustment).
     */
    OperationResult executeWarehouseOperation(ExecuteOperationCommand command);

    /** Public warehouse catalogue snapshot. */
    record WarehouseView(UUID warehouseId, String code, String name, boolean active) {

        public WarehouseView {
            Objects.requireNonNull(warehouseId, "warehouseId");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(name, "name");
        }
    }

    /** Public storage cell catalogue snapshot. */
    record StorageCellView(UUID storageCellId, UUID warehouseId, String code, boolean active) {

        public StorageCellView {
            Objects.requireNonNull(storageCellId, "storageCellId");
            Objects.requireNonNull(warehouseId, "warehouseId");
            Objects.requireNonNull(code, "code");
        }
    }

    /** Create warehouse catalogue command. */
    record CreateWarehouseCommand(String code, String name, boolean active) {

        public CreateWarehouseCommand {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(name, "name");
        }
    }

    /** Create storage cell catalogue command. */
    record CreateStorageCellCommand(UUID warehouseId, String code, boolean active) {

        public CreateStorageCellCommand {
            Objects.requireNonNull(warehouseId, "warehouseId");
            Objects.requireNonNull(code, "code");
        }
    }

    /** Warehouse-owned material reference snapshot. */
    record MaterialReferenceView(
            UUID materialReferenceId,
            String article,
            String name,
            String color,
            String size,
            String unitOfMeasure) {

        public MaterialReferenceView {
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(article, "article");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(size, "size");
            Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
        }

        /** Backward-compatible alias for {@link #article()}. */
        public String materialCode() {
            return article;
        }
    }

    /** Extended MaterialReference display snapshot for Warehouse reads. */
    record MaterialReferenceDisplayView(
            String article,
            String materialName,
            String color,
            String size,
            String unitOfMeasure) {

        public MaterialReferenceDisplayView {
            Objects.requireNonNull(article, "article");
            Objects.requireNonNull(materialName, "materialName");
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(size, "size");
            Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
        }

        /** Backward-compatible alias for {@link #article()}. */
        public String materialCode() {
            return article;
        }
    }

    /** Public stock snapshot — not a domain StockPosition. */
    record StockView(
            UUID materialReferenceId,
            String article,
            String materialName,
            String color,
            String size,
            String unitOfMeasure,
            String warehouse,
            String storageCell,
            BigDecimal quantity,
            StockStateView stockState,
            String materialCode,
            UUID warehouseId,
            UUID storageCellId) {

        public StockView {
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(article, "article");
            Objects.requireNonNull(materialName, "materialName");
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(size, "size");
            Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
            Objects.requireNonNull(warehouse, "warehouse");
            Objects.requireNonNull(storageCell, "storageCell");
            Objects.requireNonNull(quantity, "quantity");
            Objects.requireNonNull(stockState, "stockState");
            Objects.requireNonNull(materialCode, "materialCode");
            Objects.requireNonNull(warehouseId, "warehouseId");
            Objects.requireNonNull(storageCellId, "storageCellId");
        }

        public static StockView of(
                UUID materialReferenceId,
                String article,
                String materialName,
                String color,
                String size,
                String unitOfMeasure,
                String warehouse,
                String storageCell,
                BigDecimal quantity,
                StockStateView stockState,
                UUID warehouseId,
                UUID storageCellId) {
            return new StockView(
                    materialReferenceId,
                    article,
                    materialName,
                    color,
                    size,
                    unitOfMeasure,
                    warehouse,
                    storageCell,
                    quantity,
                    stockState,
                    article,
                    warehouseId,
                    storageCellId);
        }
    }

    enum StockStateView {
        AVAILABLE,
        IN_TRANSIT,
        BLOCKED
    }

    enum AvailabilityStatus {
        AVAILABLE,
        INSUFFICIENT
    }

    record AvailabilityResult(
            AvailabilityStatus status,
            String materialCode,
            BigDecimal requestedQuantity,
            BigDecimal availableQuantity) {

        public AvailabilityResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(materialCode, "materialCode");
            Objects.requireNonNull(requestedQuantity, "requestedQuantity");
            Objects.requireNonNull(availableQuantity, "availableQuantity");
        }

        public boolean isAvailable() {
            return status == AvailabilityStatus.AVAILABLE;
        }
    }

    enum ReservationTargetTypeView {
        ORDER,
        PRODUCTION_DEMAND
    }

    record CreateReservationLinkCommand(
            UUID materialReferenceId,
            ReservationTargetTypeView targetType,
            String targetReference,
            BigDecimal quantity) {

        public CreateReservationLinkCommand {
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(targetType, "targetType");
            Objects.requireNonNull(targetReference, "targetReference");
            Objects.requireNonNull(quantity, "quantity");
        }
    }

    record ReservationLinkView(
            UUID linkId,
            UUID materialReferenceId,
            String materialCode,
            ReservationTargetTypeView targetType,
            String targetReference,
            BigDecimal quantity,
            Instant createdAt) {

        public ReservationLinkView {
            Objects.requireNonNull(linkId, "linkId");
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(materialCode, "materialCode");
            Objects.requireNonNull(targetType, "targetType");
            Objects.requireNonNull(targetReference, "targetReference");
            Objects.requireNonNull(quantity, "quantity");
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    /**
     * Warehouse Operation kinds exposed through Public API.
     *
     * <p>Transfer is two-stage: {@link #TRANSFER_SEND} then {@link #TRANSFER_RECEIVE}.
     */
    enum OperationKind {
        RECEIPT,
        MOVE,
        TRANSFER_SEND,
        TRANSFER_RECEIVE,
        CONSUMPTION,
        ADJUSTMENT
    }

    /**
     * Command to execute a Warehouse Operation.
     *
     * <p>Field usage by kind:
     *
     * <ul>
     *   <li>RECEIPT — materialCode = article; materialName/color/size/unitOfMeasure required;
     *       warehouseId, storageCellId, quantity (positive)
     *   <li>MOVE / CONSUMPTION / ADJUSTMENT / TRANSFER_* — materialReferenceId required;
     *       warehouseId, storageCellId, quantity
     * </ul>
     */
    record ExecuteOperationCommand(
            OperationKind kind,
            String materialCode,
            BigDecimal quantity,
            UUID warehouseId,
            UUID storageCellId,
            UUID destinationWarehouseId,
            UUID destinationStorageCellId,
            String materialName,
            String color,
            String size,
            String unitOfMeasure,
            UUID materialReferenceId) {

        public ExecuteOperationCommand {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(quantity, "quantity");
            Objects.requireNonNull(warehouseId, "warehouseId");
            Objects.requireNonNull(storageCellId, "storageCellId");
        }

        public static ExecuteOperationCommand receipt(
                String article,
                String name,
                String color,
                String size,
                String unitOfMeasure,
                BigDecimal quantity,
                UUID warehouseId,
                UUID storageCellId) {
            return new ExecuteOperationCommand(
                    OperationKind.RECEIPT,
                    article,
                    quantity,
                    warehouseId,
                    storageCellId,
                    null,
                    null,
                    name,
                    color,
                    size,
                    unitOfMeasure,
                    null);
        }

        public static ExecuteOperationCommand move(
                UUID materialReferenceId,
                BigDecimal quantity,
                UUID sourceWarehouseId,
                UUID sourceCellId,
                UUID destinationWarehouseId,
                UUID destinationCellId) {
            return new ExecuteOperationCommand(
                    OperationKind.MOVE,
                    null,
                    quantity,
                    sourceWarehouseId,
                    sourceCellId,
                    destinationWarehouseId,
                    destinationCellId,
                    null,
                    null,
                    null,
                    null,
                    materialReferenceId);
        }

        public static ExecuteOperationCommand transferSend(
                UUID materialReferenceId,
                BigDecimal quantity,
                UUID sourceWarehouseId,
                UUID sourceCellId,
                UUID destinationWarehouseId) {
            return new ExecuteOperationCommand(
                    OperationKind.TRANSFER_SEND,
                    null,
                    quantity,
                    sourceWarehouseId,
                    sourceCellId,
                    destinationWarehouseId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    materialReferenceId);
        }

        public static ExecuteOperationCommand transferReceive(
                UUID materialReferenceId,
                BigDecimal quantity,
                UUID sourceWarehouseId,
                UUID sourceCellId,
                UUID destinationWarehouseId,
                UUID destinationCellId) {
            return new ExecuteOperationCommand(
                    OperationKind.TRANSFER_RECEIVE,
                    null,
                    quantity,
                    sourceWarehouseId,
                    sourceCellId,
                    destinationWarehouseId,
                    destinationCellId,
                    null,
                    null,
                    null,
                    null,
                    materialReferenceId);
        }

        public static ExecuteOperationCommand consumption(
                UUID materialReferenceId,
                BigDecimal quantity,
                UUID warehouseId,
                UUID storageCellId) {
            return new ExecuteOperationCommand(
                    OperationKind.CONSUMPTION,
                    null,
                    quantity,
                    warehouseId,
                    storageCellId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    materialReferenceId);
        }

        public static ExecuteOperationCommand adjustment(
                UUID materialReferenceId,
                BigDecimal quantityDelta,
                UUID warehouseId,
                UUID storageCellId) {
            return new ExecuteOperationCommand(
                    OperationKind.ADJUSTMENT,
                    null,
                    quantityDelta,
                    warehouseId,
                    storageCellId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    materialReferenceId);
        }
    }

    record OperationResult(
            UUID operationId,
            OperationKind kind,
            String status,
            UUID materialReferenceId,
            String materialCode,
            UUID warehouseId,
            UUID storageCellId,
            BigDecimal quantity) {

        public OperationResult {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(materialCode, "materialCode");
            Objects.requireNonNull(warehouseId, "warehouseId");
            Objects.requireNonNull(storageCellId, "storageCellId");
            Objects.requireNonNull(quantity, "quantity");
        }
    }
}
