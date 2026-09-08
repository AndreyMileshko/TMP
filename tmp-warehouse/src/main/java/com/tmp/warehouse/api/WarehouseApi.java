package com.tmp.warehouse.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Combined Warehouse public surface for Stage 6 UI bootstrap.
 *
 * <p>Cross-capability integration must depend on {@link WarehouseQueryApi} and {@link
 * WarehouseCommandApi} separately rather than this aggregate.
 */
public interface WarehouseApi extends WarehouseQueryApi, WarehouseCommandApi {

    /** Normalized material identity for exact availability queries. */
    record MaterialIdentityRequest(String article, String color, String size, String unitOfMeasure) {

        public MaterialIdentityRequest {
            java.util.Objects.requireNonNull(article, "article");
            color = color == null ? "" : color.trim();
            size = size == null ? "" : size.trim();
            unitOfMeasure = unitOfMeasure == null ? "" : unitOfMeasure.trim();
        }

        public static MaterialIdentityRequest of(
                String article, String color, String size, String unitOfMeasure) {
            return new MaterialIdentityRequest(article, color, size, unitOfMeasure);
        }
    }

    record ReceiptCommand(
            String article,
            String name,
            String color,
            String size,
            String unitOfMeasure,
            java.math.BigDecimal quantity,
            UUID warehouseId,
            UUID storageCellId) {

        public ReceiptCommand {
            java.util.Objects.requireNonNull(article, "article");
            java.util.Objects.requireNonNull(name, "name");
            java.util.Objects.requireNonNull(quantity, "quantity");
            java.util.Objects.requireNonNull(warehouseId, "warehouseId");
            java.util.Objects.requireNonNull(storageCellId, "storageCellId");
        }
    }

    record ConsumptionCommand(
            UUID materialReferenceId,
            java.math.BigDecimal quantity,
            UUID warehouseId,
            UUID storageCellId) {

        public ConsumptionCommand {
            java.util.Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            java.util.Objects.requireNonNull(quantity, "quantity");
            java.util.Objects.requireNonNull(warehouseId, "warehouseId");
            java.util.Objects.requireNonNull(storageCellId, "storageCellId");
        }
    }

    record CreateTransferDraftCommand(
            UUID materialReferenceId,
            java.math.BigDecimal quantity,
            UUID sourceWarehouseId,
            UUID sourceStorageCellId,
            UUID destinationWarehouseId,
            UUID destinationStorageCellId) {

        public CreateTransferDraftCommand {
            java.util.Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            java.util.Objects.requireNonNull(quantity, "quantity");
            java.util.Objects.requireNonNull(sourceWarehouseId, "sourceWarehouseId");
            java.util.Objects.requireNonNull(sourceStorageCellId, "sourceStorageCellId");
            java.util.Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
            java.util.Objects.requireNonNull(destinationStorageCellId, "destinationStorageCellId");
        }
    }

    record TransferRequestView(
            UUID operationId,
            String status,
            UUID materialReferenceId,
            java.math.BigDecimal quantity,
            UUID sourceWarehouseId,
            UUID sourceStorageCellId,
            UUID destinationWarehouseId,
            UUID destinationStorageCellId) {

        public TransferRequestView {
            java.util.Objects.requireNonNull(operationId, "operationId");
            java.util.Objects.requireNonNull(status, "status");
            java.util.Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            java.util.Objects.requireNonNull(quantity, "quantity");
            java.util.Objects.requireNonNull(sourceWarehouseId, "sourceWarehouseId");
            java.util.Objects.requireNonNull(sourceStorageCellId, "sourceStorageCellId");
            java.util.Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
            // destinationStorageCellId may be null for deferred-destination drafts (Stage 3.5.3)
        }
    }

    /** One line of a multi-line Transfer Document command / view. */
    record TransferDocumentLineInput(
            UUID lineId, UUID materialReferenceId, BigDecimal quantity, Integer lineOrder) {

        public TransferDocumentLineInput {
            java.util.Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            java.util.Objects.requireNonNull(quantity, "quantity");
        }
    }

    record TransferDocumentLineView(
            UUID lineId, UUID materialReferenceId, BigDecimal quantity, int lineOrder) {

        public TransferDocumentLineView {
            java.util.Objects.requireNonNull(lineId, "lineId");
            java.util.Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            java.util.Objects.requireNonNull(quantity, "quantity");
        }
    }

    /**
     * Create Warehouse-owned multi-line Transfer Document (Document Engine DRAFT + typed payload).
     * Empty initial lines are allowed.
     */
    record CreateTransferDocumentCommand(
            UUID sourceWarehouseId,
            UUID destinationWarehouseId,
            List<TransferDocumentLineInput> lines) {

        public CreateTransferDocumentCommand {
            java.util.Objects.requireNonNull(sourceWarehouseId, "sourceWarehouseId");
            java.util.Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    /**
     * Atomically replace DRAFT Transfer Document warehouses and lines using payload optimistic
     * lock.
     */
    record UpdateTransferDocumentCommand(
            UUID documentId,
            long expectedPayloadRevision,
            UUID sourceWarehouseId,
            UUID destinationWarehouseId,
            List<TransferDocumentLineInput> lines) {

        public UpdateTransferDocumentCommand {
            java.util.Objects.requireNonNull(documentId, "documentId");
            java.util.Objects.requireNonNull(sourceWarehouseId, "sourceWarehouseId");
            java.util.Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    /** Combined Document Engine metadata + Warehouse Transfer Document payload view. */
    record TransferDocumentView(
            UUID documentId,
            String documentNumber,
            String title,
            String documentStatus,
            UUID sourceWarehouseId,
            UUID destinationWarehouseId,
            int payloadSchemaVersion,
            long payloadRevision,
            List<TransferDocumentLineView> lines) {

        public TransferDocumentView {
            java.util.Objects.requireNonNull(documentId, "documentId");
            java.util.Objects.requireNonNull(documentNumber, "documentNumber");
            java.util.Objects.requireNonNull(title, "title");
            java.util.Objects.requireNonNull(documentStatus, "documentStatus");
            java.util.Objects.requireNonNull(sourceWarehouseId, "sourceWarehouseId");
            java.util.Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    /**
     * Logical transfer status for a send reference (or receive operation).
     *
     * <p>{@code status} is {@code DRAFT}, {@code SENT}, {@code RECEIVED}, or the underlying
     * operation status when the send failed. After receive, a query by the original send id returns
     * {@code RECEIVED} rather than {@code COMPLETED}.
     */
    record TransferStatusView(
            UUID operationId,
            OperationKind kind,
            String status,
            UUID materialReferenceId,
            java.math.BigDecimal quantity,
            UUID warehouseId,
            UUID storageCellId,
            UUID destinationWarehouseId,
            UUID destinationStorageCellId,
            UUID receiveOperationId) {

        public TransferStatusView {
            java.util.Objects.requireNonNull(operationId, "operationId");
            java.util.Objects.requireNonNull(kind, "kind");
            java.util.Objects.requireNonNull(status, "status");
        }
    }

    /** @deprecated Use {@link WarehouseQueryApi#checkAvailabilityByLegacyArticle} */
    @Deprecated
    default AvailabilityResult checkAvailability(String materialCode, java.math.BigDecimal quantity) {
        return checkAvailabilityByLegacyArticle(materialCode, quantity);
    }

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

    /**
     * One material demand line for automatic source routing. {@code demandKey} correlates batch
     * input to result; duplicate material ids are not merged.
     */
    record MaterialDemand(String demandKey, UUID materialReferenceId, BigDecimal quantity) {

        public MaterialDemand {
            Objects.requireNonNull(demandKey, "demandKey");
            if (demandKey.isBlank()) {
                throw new IllegalArgumentException("demandKey must not be blank");
            }
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(quantity, "quantity");
        }
    }

    /** Outcome of automatic source warehouse selection. */
    enum MaterialSourceRoutingOutcome {
        SOURCE_SELECTED,
        NO_AVAILABLE_SOURCE
    }

    /**
     * Suggested pick from one source cell. Suggestion only — not a reservation or stock mutation.
     * Future UI may replace allocations before send.
     */
    record SourceCellSuggestion(
            UUID storageCellId,
            String storageCellCode,
            BigDecimal availableQuantity,
            BigDecimal suggestedQuantity) {

        public SourceCellSuggestion {
            Objects.requireNonNull(storageCellId, "storageCellId");
            Objects.requireNonNull(storageCellCode, "storageCellCode");
            Objects.requireNonNull(availableQuantity, "availableQuantity");
            Objects.requireNonNull(suggestedQuantity, "suggestedQuantity");
        }
    }

    /**
     * Automatic source routing result for one demand line. Destination cell is intentionally
     * absent (deferred to receive).
     */
    record MaterialSourceRoutingResult(
            String demandKey,
            UUID materialReferenceId,
            BigDecimal quantity,
            MaterialSourceRoutingOutcome outcome,
            UUID sourceWarehouseId,
            String sourceWarehouseCode,
            BigDecimal availableAtSelectedSource,
            BigDecimal routedQuantity,
            BigDecimal uncoveredQuantity,
            List<SourceCellSuggestion> sourceCellSuggestions) {

        public MaterialSourceRoutingResult {
            Objects.requireNonNull(demandKey, "demandKey");
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(quantity, "quantity");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(availableAtSelectedSource, "availableAtSelectedSource");
            Objects.requireNonNull(routedQuantity, "routedQuantity");
            Objects.requireNonNull(uncoveredQuantity, "uncoveredQuantity");
            Objects.requireNonNull(sourceCellSuggestions, "sourceCellSuggestions");
            sourceCellSuggestions = List.copyOf(sourceCellSuggestions);
            if (outcome == MaterialSourceRoutingOutcome.SOURCE_SELECTED) {
                Objects.requireNonNull(sourceWarehouseId, "sourceWarehouseId");
                Objects.requireNonNull(sourceWarehouseCode, "sourceWarehouseCode");
            }
        }
    }
}
