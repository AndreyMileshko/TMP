package com.tmp.order.application.processing;

import com.tmp.order.application.payload.DocumentId;

/**
 * Thrown when inserting a processing record whose {@link DocumentId} + {@link ProcessingOperation}
 * already exists (Specification §16 uniqueness).
 */
public final class DuplicateProcessingRecordException extends RuntimeException {

    private final DocumentId documentId;
    private final ProcessingOperation operation;

    public DuplicateProcessingRecordException(DocumentId documentId, ProcessingOperation operation) {
        super(
                "Processing record already exists for document "
                        + documentId
                        + " operation "
                        + operation);
        this.documentId = documentId;
        this.operation = operation;
    }

    public DocumentId documentId() {
        return documentId;
    }

    public ProcessingOperation operation() {
        return operation;
    }
}
