package com.tmp.order.application.processing;

import com.tmp.order.application.payload.DocumentId;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.domain.PayloadRevision;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable processing record for a posted Order Management document (Specification §16).
 *
 * <p>Uniqueness key: {@link DocumentId} + {@link ProcessingOperation}. {@link ResultReference} is
 * internal only and is never returned from {@code onPost}.
 */
public final class ProcessingRecord {

    private final DocumentId documentId;
    private final DocumentTypeCode documentTypeCode;
    private final ProcessingOperation operation;
    private final ProcessingStatus processingStatus;
    private final PayloadRevision payloadRevision;
    private final Instant processedAt;
    private final ResultReference resultReference;

    private ProcessingRecord(
            DocumentId documentId,
            DocumentTypeCode documentTypeCode,
            ProcessingOperation operation,
            ProcessingStatus processingStatus,
            PayloadRevision payloadRevision,
            Instant processedAt,
            ResultReference resultReference) {
        this.documentId = Objects.requireNonNull(documentId, "documentId");
        this.documentTypeCode = Objects.requireNonNull(documentTypeCode, "documentTypeCode");
        this.operation = Objects.requireNonNull(operation, "operation");
        this.processingStatus = Objects.requireNonNull(processingStatus, "processingStatus");
        this.payloadRevision = Objects.requireNonNull(payloadRevision, "payloadRevision");
        this.processedAt = Objects.requireNonNull(processedAt, "processedAt");
        this.resultReference = resultReference;
    }

    public static ProcessingRecord completedPost(
            DocumentId documentId,
            DocumentTypeCode documentTypeCode,
            PayloadRevision payloadRevision,
            Instant processedAt,
            ResultReference resultReference) {
        return new ProcessingRecord(
                documentId,
                documentTypeCode,
                ProcessingOperation.POST,
                ProcessingStatus.COMPLETED,
                payloadRevision,
                processedAt,
                resultReference);
    }

    public static ProcessingRecord rehydrate(
            DocumentId documentId,
            DocumentTypeCode documentTypeCode,
            ProcessingOperation operation,
            ProcessingStatus processingStatus,
            PayloadRevision payloadRevision,
            Instant processedAt,
            ResultReference resultReference) {
        return new ProcessingRecord(
                documentId,
                documentTypeCode,
                operation,
                processingStatus,
                payloadRevision,
                processedAt,
                resultReference);
    }

    public DocumentId documentId() {
        return documentId;
    }

    public DocumentTypeCode documentTypeCode() {
        return documentTypeCode;
    }

    public ProcessingOperation operation() {
        return operation;
    }

    public ProcessingStatus processingStatus() {
        return processingStatus;
    }

    public PayloadRevision payloadRevision() {
        return payloadRevision;
    }

    public Instant processedAt() {
        return processedAt;
    }

    public Optional<ResultReference> resultReference() {
        return Optional.ofNullable(resultReference);
    }
}
