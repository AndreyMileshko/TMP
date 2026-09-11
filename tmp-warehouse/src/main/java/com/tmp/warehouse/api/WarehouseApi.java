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
            long documentVersion,
            UUID sourceWarehouseId,
            UUID destinationWarehouseId,
            int payloadSchemaVersion,
            long payloadRevision,
            List<TransferDocumentLineView> lines,
            UUID continuationOfDocumentId,
            String continuationReason,
            String settlementState,
            Long operationalRevision,
            String settlementDecision,
            String rejectionReason) {

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
     * Source-cell suggestions for one Transfer Document line (fixed source warehouse). Planning
     * only — does not reserve or mutate stock.
     */
    record TransferDocumentSourceSuggestionLine(
            UUID lineId,
            UUID materialReferenceId,
            BigDecimal requiredQuantity,
            List<SourceCellSuggestion> suggestions) {

        public TransferDocumentSourceSuggestionLine {
            java.util.Objects.requireNonNull(lineId, "lineId");
            java.util.Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            java.util.Objects.requireNonNull(requiredQuantity, "requiredQuantity");
            suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
        }
    }

    /**
     * Outstanding return target for one send allocation of a RETURN_PENDING Transfer Document.
     * Default cell is the original source cell from send allocation.
     */
    record TransferDocumentReturnPlanItem(
            UUID lineId,
            UUID materialReferenceId,
            BigDecimal outstandingQuantity,
            UUID defaultReturnStorageCellId,
            String defaultReturnStorageCellCode) {

        public TransferDocumentReturnPlanItem {
            java.util.Objects.requireNonNull(lineId, "lineId");
            java.util.Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            java.util.Objects.requireNonNull(outstandingQuantity, "outstandingQuantity");
            java.util.Objects.requireNonNull(
                    defaultReturnStorageCellId, "defaultReturnStorageCellId");
            java.util.Objects.requireNonNull(
                    defaultReturnStorageCellCode, "defaultReturnStorageCellCode");
        }
    }

    /** One destination-cell allocation for Transfer Document physical RECEIVE (Stage 3.5.8.1). */
    record TransferDocumentDestinationAllocationInput(
            UUID lineId, UUID destinationStorageCellId, BigDecimal quantity) {

        public TransferDocumentDestinationAllocationInput {
            java.util.Objects.requireNonNull(lineId, "lineId");
            java.util.Objects.requireNonNull(destinationStorageCellId, "destinationStorageCellId");
            java.util.Objects.requireNonNull(quantity, "quantity");
        }
    }

    /**
     * Document-level acceptance of a POSTED Transfer Document (Stage 3.5.8.1 / 3.5.8.2). Supports
     * full or partial acceptance: total accepted must be {@code > 0}; per-line accepted must be
     * {@code <=} sent. Full reject uses {@link WarehouseCommandApi#rejectTransferDocument}.
     */
    record ReceiveTransferDocumentCommand(
            UUID documentId,
            long expectedOperationalRevision,
            List<TransferDocumentDestinationAllocationInput> destinationAllocations) {

        public ReceiveTransferDocumentCommand {
            java.util.Objects.requireNonNull(documentId, "documentId");
            destinationAllocations =
                    destinationAllocations == null
                            ? List.of()
                            : List.copyOf(destinationAllocations);
        }
    }

    /** Compact result of a successful Transfer Document receive (full or partial). */
    record TransferDocumentReceiveResult(
            UUID documentId,
            String documentStatus,
            long documentVersion,
            String settlementState,
            String decision,
            long operationalRevision,
            List<UUID> receiveOperationIds,
            UUID continuationDocumentId) {

        public TransferDocumentReceiveResult {
            java.util.Objects.requireNonNull(documentId, "documentId");
            java.util.Objects.requireNonNull(documentStatus, "documentStatus");
            java.util.Objects.requireNonNull(settlementState, "settlementState");
            receiveOperationIds =
                    receiveOperationIds == null ? List.of() : List.copyOf(receiveOperationIds);
        }
    }

    /**
     * Whole-document reject of a POSTED Transfer Document awaiting receipt (Stage 3.5.8.3).
     * {@code rejectionReason} is mandatory (non-blank after trim, max 500). Caller must not supply
     * rejectedBy — resolved from the authenticated session.
     */
    record RejectTransferDocumentCommand(
            UUID documentId, long expectedOperationalRevision, String rejectionReason) {

        public RejectTransferDocumentCommand {
            java.util.Objects.requireNonNull(documentId, "documentId");
        }
    }

    /** Compact result of a successful Transfer Document reject. */
    record TransferDocumentRejectResult(
            UUID documentId,
            String documentStatus,
            String settlementState,
            String decision,
            long operationalRevision,
            String rejectionReason) {

        public TransferDocumentRejectResult {
            java.util.Objects.requireNonNull(documentId, "documentId");
            java.util.Objects.requireNonNull(documentStatus, "documentStatus");
            java.util.Objects.requireNonNull(settlementState, "settlementState");
        }
    }

    /** One return-cell allocation for Transfer Document physical RETURN (Stage 3.5.8.3). */
    record TransferDocumentReturnAllocationInput(
            UUID lineId, UUID returnStorageCellId, BigDecimal quantity) {

        public TransferDocumentReturnAllocationInput {
            java.util.Objects.requireNonNull(lineId, "lineId");
            java.util.Objects.requireNonNull(returnStorageCellId, "returnStorageCellId");
            java.util.Objects.requireNonNull(quantity, "quantity");
        }
    }

    /**
     * Physical return of outstanding Transfer Document materials to the source warehouse. Empty
     * {@code returnAllocations} uses the default plan (original source cells). Non-empty list is a
     * complete override covering outstanding quantity exactly.
     */
    record ReturnTransferMaterialsCommand(
            UUID documentId,
            long expectedOperationalRevision,
            List<TransferDocumentReturnAllocationInput> returnAllocations) {

        public ReturnTransferMaterialsCommand {
            java.util.Objects.requireNonNull(documentId, "documentId");
            returnAllocations =
                    returnAllocations == null ? List.of() : List.copyOf(returnAllocations);
        }
    }

    /** Compact result of a successful Transfer Document physical return. */
    record TransferDocumentReturnResult(
            UUID documentId,
            String documentStatus,
            String settlementState,
            String decision,
            long operationalRevision,
            List<UUID> returnOperationIds) {

        public TransferDocumentReturnResult {
            java.util.Objects.requireNonNull(documentId, "documentId");
            java.util.Objects.requireNonNull(documentStatus, "documentStatus");
            java.util.Objects.requireNonNull(settlementState, "settlementState");
            returnOperationIds =
                    returnOperationIds == null ? List.of() : List.copyOf(returnOperationIds);
        }
    }

    /** One source-cell allocation for Transfer Document physical SEND (Stage 3.5.6). */
    record TransferDocumentSourceAllocationInput(
            UUID lineId, UUID sourceStorageCellId, BigDecimal quantity) {

        public TransferDocumentSourceAllocationInput {
            java.util.Objects.requireNonNull(lineId, "lineId");
            java.util.Objects.requireNonNull(sourceStorageCellId, "sourceStorageCellId");
            java.util.Objects.requireNonNull(quantity, "quantity");
        }
    }

    /**
     * Atomically send a DRAFT Transfer Document: stage source-cell allocations and POST (physical
     * AVAILABLE → IN_TRANSIT). Destination cell remains deferred until receive.
     */
    record SendTransferDocumentCommand(
            UUID documentId,
            long expectedDocumentVersion,
            long expectedPayloadRevision,
            List<TransferDocumentSourceAllocationInput> sourceAllocations) {

        public SendTransferDocumentCommand {
            java.util.Objects.requireNonNull(documentId, "documentId");
            sourceAllocations =
                    sourceAllocations == null ? List.of() : List.copyOf(sourceAllocations);
        }
    }

    /** Compact result of a successful Transfer Document physical SEND. */
    record TransferDocumentSendResult(
            UUID documentId,
            String documentStatus,
            long documentVersion,
            long payloadRevision,
            List<UUID> sendOperationIds,
            UUID continuationDocumentId) {

        public TransferDocumentSendResult {
            java.util.Objects.requireNonNull(documentId, "documentId");
            java.util.Objects.requireNonNull(documentStatus, "documentStatus");
            sendOperationIds =
                    sendOperationIds == null ? List.of() : List.copyOf(sendOperationIds);
        }
    }

    /**
     * Stage 3.5 task kinds: preparation (DRAFT), receipt (POSTED+AWAITING_RECEIPT), return materials
     * (POSTED+RETURN_PENDING). Physical return action is Stage 3.5.8.3.
     */
    enum WarehouseTaskKind {
        TRANSFER_PREPARATION,
        TRANSFER_RECEIPT,
        RETURN_MATERIALS
    }

    /** Derived informational task state: no assignment row → NEW; assignment present → IN_WORK. */
    enum WarehouseTaskState {
        NEW,
        IN_WORK
    }

    /**
     * Compact operational inbox projection over a {@code warehouse.transfer} document.
     * Task identity is {@code documentId}. Worker fields are opaque Security UUIDs / timestamps.
     * For {@code RETURN_MATERIALS}, {@code settlementDecision}/{@code rejectionReason} expose
     * reject metadata when decision is {@code REJECTED}.
     */
    record WarehouseTaskView(
            UUID documentId,
            String documentNumber,
            WarehouseTaskKind taskKind,
            WarehouseTaskState taskState,
            UUID sourceWarehouseId,
            String sourceWarehouseCode,
            String sourceWarehouseName,
            UUID destinationWarehouseId,
            String destinationWarehouseCode,
            String destinationWarehouseName,
            int lineCount,
            UUID workingUserId,
            java.time.Instant workingSince,
            java.time.Instant createdAt,
            UUID continuationOfDocumentId,
            String continuationReason,
            String settlementState,
            Long operationalRevision,
            String settlementDecision,
            String rejectionReason) {

        public WarehouseTaskView {
            java.util.Objects.requireNonNull(documentId, "documentId");
            java.util.Objects.requireNonNull(documentNumber, "documentNumber");
            java.util.Objects.requireNonNull(taskKind, "taskKind");
            java.util.Objects.requireNonNull(taskState, "taskState");
            java.util.Objects.requireNonNull(sourceWarehouseId, "sourceWarehouseId");
            java.util.Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
            java.util.Objects.requireNonNull(createdAt, "createdAt");
            if (lineCount < 0) {
                throw new IllegalArgumentException("lineCount must not be negative");
            }
        }
    }

    /**
     * Logical transfer status for a send reference (or receive operation).
     *
     * <p>Legacy (non-document) transfers use {@code DRAFT}, {@code SENT}, {@code RECEIVED}.
     * Document-managed send allocations use settlement-aware statuses: {@code SENT}, {@code
     * PARTIALLY_RECEIVED}, {@code REJECTED}, {@code RETURNED}, {@code RECEIVED}.
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

    /** Default page size for modern Остатки summaries (aligned with Order list). */
    int STOCK_SUMMARY_DEFAULT_PAGE_SIZE = 50;

    /** Max page size for modern Остатки summaries. */
    int STOCK_SUMMARY_MAX_PAGE_SIZE = 100;

    /**
     * Material-level AVAILABLE stock summary for modern Остатки (not a persisted inventory entity).
     */
    record WarehouseStockSummaryView(
            UUID warehouseId,
            UUID materialReferenceId,
            String article,
            String name,
            String color,
            String size,
            String unitOfMeasure,
            BigDecimal availableQuantity) {

        public WarehouseStockSummaryView {
            Objects.requireNonNull(warehouseId, "warehouseId");
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(article, "article");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(size, "size");
            Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
            Objects.requireNonNull(availableQuantity, "availableQuantity");
        }
    }

    /** One active storage cell with positive AVAILABLE quantity. */
    record WarehouseStockCellView(
            UUID storageCellId, String storageCellCode, BigDecimal availableQuantity) {

        public WarehouseStockCellView {
            Objects.requireNonNull(storageCellId, "storageCellId");
            Objects.requireNonNull(storageCellCode, "storageCellCode");
            Objects.requireNonNull(availableQuantity, "availableQuantity");
        }
    }

    /** Expand details for one material on one warehouse. */
    record WarehouseMaterialStockDetailsView(
            UUID warehouseId,
            UUID materialReferenceId,
            BigDecimal totalAvailable,
            List<WarehouseStockCellView> cells) {

        public WarehouseMaterialStockDetailsView {
            Objects.requireNonNull(warehouseId, "warehouseId");
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(totalAvailable, "totalAvailable");
            Objects.requireNonNull(cells, "cells");
            cells = List.copyOf(cells);
        }
    }

    /** Paginated AVAILABLE stock summaries. */
    record WarehouseStockPage(
            List<WarehouseStockSummaryView> content,
            int pageIndex,
            int pageSize,
            long totalElements) {

        public WarehouseStockPage {
            Objects.requireNonNull(content, "content");
            content = List.copyOf(content);
            if (pageIndex < 0) {
                throw new IllegalArgumentException("pageIndex must be >= 0: " + pageIndex);
            }
            if (pageSize < 1) {
                throw new IllegalArgumentException("pageSize must be >= 1: " + pageSize);
            }
            if (totalElements < 0) {
                throw new IllegalArgumentException("totalElements must be >= 0: " + totalElements);
            }
        }

        public static WarehouseStockPage of(
                List<WarehouseStockSummaryView> content,
                int pageIndex,
                int pageSize,
                long totalElements) {
            return new WarehouseStockPage(content, pageIndex, pageSize, totalElements);
        }
    }

    /** Default page size for Warehouse History. */
    int HISTORY_DEFAULT_PAGE_SIZE = STOCK_SUMMARY_DEFAULT_PAGE_SIZE;

    /** Max page size for Warehouse History. */
    int HISTORY_MAX_PAGE_SIZE = STOCK_SUMMARY_MAX_PAGE_SIZE;

    /**
     * Server-side history filter. {@code fromInclusive} / {@code toExclusive} use Instant half-open
     * range ({@code occurredAt >= from && occurredAt < to}). {@code operationType} null = all
     * physical types. {@code materialSearch} matches article or name (trim, case-insensitive).
     */
    record WarehouseHistoryFilter(
            Instant fromInclusive,
            Instant toExclusive,
            String materialSearch,
            String operationType) {

        public WarehouseHistoryFilter {
            Objects.requireNonNull(fromInclusive, "fromInclusive");
            Objects.requireNonNull(toExclusive, "toExclusive");
            if (!fromInclusive.isBefore(toExclusive)) {
                throw new IllegalArgumentException(
                        "fromInclusive must be before toExclusive: "
                                + fromInclusive
                                + " / "
                                + toExclusive);
            }
        }
    }

    /** One logical Warehouse History row (completed physical operation). */
    record WarehouseHistoryEntryView(
            UUID entryId,
            Instant occurredAt,
            String operationType,
            String operationDisplayName,
            UUID materialReferenceId,
            String materialArticle,
            String materialName,
            String unitOfMeasure,
            BigDecimal quantity,
            UUID sourceWarehouseId,
            String sourceWarehouseName,
            UUID sourceCellId,
            String sourceCellCode,
            UUID destinationWarehouseId,
            String destinationWarehouseName,
            UUID destinationCellId,
            String destinationCellCode,
            UUID documentId,
            String documentNumber,
            UUID actorUserId,
            String actorDisplayName) {

        public WarehouseHistoryEntryView {
            Objects.requireNonNull(entryId, "entryId");
            Objects.requireNonNull(occurredAt, "occurredAt");
            Objects.requireNonNull(operationType, "operationType");
            Objects.requireNonNull(operationDisplayName, "operationDisplayName");
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(materialArticle, "materialArticle");
            Objects.requireNonNull(materialName, "materialName");
            Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
            Objects.requireNonNull(quantity, "quantity");
        }
    }

    /** Paginated Warehouse History. */
    record WarehouseHistoryPage(
            List<WarehouseHistoryEntryView> content,
            int pageIndex,
            int pageSize,
            long totalElements) {

        public WarehouseHistoryPage {
            Objects.requireNonNull(content, "content");
            content = List.copyOf(content);
            if (pageIndex < 0) {
                throw new IllegalArgumentException("pageIndex must be >= 0: " + pageIndex);
            }
            if (pageSize < 1) {
                throw new IllegalArgumentException("pageSize must be >= 1: " + pageSize);
            }
            if (totalElements < 0) {
                throw new IllegalArgumentException("totalElements must be >= 0: " + totalElements);
            }
        }

        public static WarehouseHistoryPage of(
                List<WarehouseHistoryEntryView> content,
                int pageIndex,
                int pageSize,
                long totalElements) {
            return new WarehouseHistoryPage(content, pageIndex, pageSize, totalElements);
        }
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
     * <p>Transfer stages: {@link #TRANSFER_SEND}, {@link #TRANSFER_RECEIVE}, and {@link
     * #TRANSFER_RETURN}. {@link #TRANSFER_RETURN} is a read/result representation only — it must not
     * be executed via unrestricted {@link WarehouseCommandApi#executeWarehouseOperation}; physical
     * return is owned by Transfer Document settlement.
     */
    enum OperationKind {
        RECEIPT,
        MOVE,
        TRANSFER_SEND,
        TRANSFER_RECEIVE,
        TRANSFER_RETURN,
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
