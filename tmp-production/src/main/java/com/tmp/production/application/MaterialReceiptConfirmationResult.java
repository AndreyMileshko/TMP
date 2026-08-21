package com.tmp.production.application;

import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.ProductionMaterialTransferId;
import com.tmp.production.domain.SourceOrderId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Production-owned immutable application result of «Подтвердить получение».
 *
 * <p>Not a persisted Production lifecycle or Warehouse domain model. {@link #confirmedAt()} is the
 * Production use-case completion (or idempotent check) time — not a Warehouse receive timestamp.
 */
public record MaterialReceiptConfirmationResult(
        ProductionMaterialTransferId logicalTransferId,
        SourceOrderId sourceOrderId,
        Instant confirmedAt,
        MaterialReceiptConfirmationStatus status,
        List<MaterialReceiptReferenceResult> references) {

    public MaterialReceiptConfirmationResult {
        Objects.requireNonNull(logicalTransferId, "logicalTransferId");
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        Objects.requireNonNull(confirmedAt, "confirmedAt");
        Objects.requireNonNull(status, "status");
        references = List.copyOf(Objects.requireNonNull(references, "references"));
        if (references.isEmpty()) {
            throw new IllegalArgumentException("references must not be empty");
        }
    }

    /** Application-level outcome of the receipt confirmation command. */
    public enum MaterialReceiptConfirmationStatus {
        /** At least one Warehouse receive was performed in this command. */
        RECEIVED,
        /** All Warehouse refs were already RECEIVED; no Warehouse receive calls made. */
        ALREADY_RECEIVED
    }

    /** Per Warehouse transfer reference outcome derived from Warehouse public data. */
    public record MaterialReceiptReferenceResult(
            UUID warehouseSendOperationId,
            UUID receiveOperationId,
            MaterialReferenceId materialReferenceId,
            BigDecimal quantity,
            String resultingStatus) {

        public MaterialReceiptReferenceResult {
            Objects.requireNonNull(warehouseSendOperationId, "warehouseSendOperationId");
            Objects.requireNonNull(receiveOperationId, "receiveOperationId");
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(quantity, "quantity");
            Objects.requireNonNull(resultingStatus, "resultingStatus");
        }
    }
}
