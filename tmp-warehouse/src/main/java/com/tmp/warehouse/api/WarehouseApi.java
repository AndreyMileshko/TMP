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
     * Returns current stock positions for the given material reference.
     *
     * @param materialCode material reference from Specification context
     * @return stock views (material, warehouse, cell, quantity, state); empty when none
     */
    List<StockView> getStock(String materialCode);

    /**
     * Returns stock for material filtered by warehouse and storage cell (Specification §17).
     */
    List<StockView> getStock(String materialCode, UUID warehouseId, UUID storageCellId);

    /**
     * Returns all stock positions for a warehouse.
     */
    List<StockView> getStockByWarehouse(UUID warehouseId);

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

    /** Public stock snapshot — not a domain StockPosition. */
    record StockView(
            String materialCode,
            UUID warehouseId,
            UUID storageCellId,
            BigDecimal quantity,
            StockStateView stockState) {

        public StockView {
            Objects.requireNonNull(materialCode, "materialCode");
            Objects.requireNonNull(warehouseId, "warehouseId");
            Objects.requireNonNull(storageCellId, "storageCellId");
            Objects.requireNonNull(quantity, "quantity");
            Objects.requireNonNull(stockState, "stockState");
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
            String materialCode,
            ReservationTargetTypeView targetType,
            String targetReference,
            BigDecimal quantity) {

        public CreateReservationLinkCommand {
            Objects.requireNonNull(materialCode, "materialCode");
            Objects.requireNonNull(targetType, "targetType");
            Objects.requireNonNull(targetReference, "targetReference");
            Objects.requireNonNull(quantity, "quantity");
        }
    }

    record ReservationLinkView(
            UUID linkId,
            String materialCode,
            ReservationTargetTypeView targetType,
            String targetReference,
            BigDecimal quantity,
            Instant createdAt) {

        public ReservationLinkView {
            Objects.requireNonNull(linkId, "linkId");
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
     *   <li>RECEIPT / CONSUMPTION — warehouseId, storageCellId, quantity (positive)
     *   <li>MOVE — warehouseId/storageCellId = source; destinationWarehouseId/destinationStorageCellId
     *       = destination; quantity positive
     *   <li>TRANSFER_SEND — warehouseId/storageCellId = source; destinationWarehouseId required;
     *       quantity positive
     *   <li>TRANSFER_RECEIVE — warehouseId/storageCellId = source IN_TRANSIT; destination* =
     *       receive location; quantity positive
     *   <li>ADJUSTMENT — warehouseId, storageCellId; quantity is signed delta (non-zero)
     * </ul>
     */
    record ExecuteOperationCommand(
            OperationKind kind,
            String materialCode,
            BigDecimal quantity,
            UUID warehouseId,
            UUID storageCellId,
            UUID destinationWarehouseId,
            UUID destinationStorageCellId) {

        public ExecuteOperationCommand {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(materialCode, "materialCode");
            Objects.requireNonNull(quantity, "quantity");
            Objects.requireNonNull(warehouseId, "warehouseId");
            Objects.requireNonNull(storageCellId, "storageCellId");
        }

        public static ExecuteOperationCommand receipt(
                String materialCode, BigDecimal quantity, UUID warehouseId, UUID storageCellId) {
            return new ExecuteOperationCommand(
                    OperationKind.RECEIPT,
                    materialCode,
                    quantity,
                    warehouseId,
                    storageCellId,
                    null,
                    null);
        }

        public static ExecuteOperationCommand move(
                String materialCode,
                BigDecimal quantity,
                UUID sourceWarehouseId,
                UUID sourceCellId,
                UUID destinationWarehouseId,
                UUID destinationCellId) {
            return new ExecuteOperationCommand(
                    OperationKind.MOVE,
                    materialCode,
                    quantity,
                    sourceWarehouseId,
                    sourceCellId,
                    destinationWarehouseId,
                    destinationCellId);
        }

        public static ExecuteOperationCommand transferSend(
                String materialCode,
                BigDecimal quantity,
                UUID sourceWarehouseId,
                UUID sourceCellId,
                UUID destinationWarehouseId) {
            return new ExecuteOperationCommand(
                    OperationKind.TRANSFER_SEND,
                    materialCode,
                    quantity,
                    sourceWarehouseId,
                    sourceCellId,
                    destinationWarehouseId,
                    null);
        }

        public static ExecuteOperationCommand transferReceive(
                String materialCode,
                BigDecimal quantity,
                UUID sourceWarehouseId,
                UUID sourceCellId,
                UUID destinationWarehouseId,
                UUID destinationCellId) {
            return new ExecuteOperationCommand(
                    OperationKind.TRANSFER_RECEIVE,
                    materialCode,
                    quantity,
                    sourceWarehouseId,
                    sourceCellId,
                    destinationWarehouseId,
                    destinationCellId);
        }

        public static ExecuteOperationCommand consumption(
                String materialCode, BigDecimal quantity, UUID warehouseId, UUID storageCellId) {
            return new ExecuteOperationCommand(
                    OperationKind.CONSUMPTION,
                    materialCode,
                    quantity,
                    warehouseId,
                    storageCellId,
                    null,
                    null);
        }

        public static ExecuteOperationCommand adjustment(
                String materialCode, BigDecimal quantityDelta, UUID warehouseId, UUID storageCellId) {
            return new ExecuteOperationCommand(
                    OperationKind.ADJUSTMENT,
                    materialCode,
                    quantityDelta,
                    warehouseId,
                    storageCellId,
                    null,
                    null);
        }
    }

    record OperationResult(
            UUID operationId,
            OperationKind kind,
            String status,
            String materialCode,
            UUID warehouseId,
            UUID storageCellId,
            BigDecimal quantity) {

        public OperationResult {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(materialCode, "materialCode");
            Objects.requireNonNull(warehouseId, "warehouseId");
            Objects.requireNonNull(storageCellId, "storageCellId");
            Objects.requireNonNull(quantity, "quantity");
        }
    }
}
