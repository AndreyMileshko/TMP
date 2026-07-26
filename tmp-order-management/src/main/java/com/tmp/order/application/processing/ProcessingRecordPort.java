package com.tmp.order.application.processing;

import com.tmp.order.application.payload.DocumentId;
import java.util.Optional;

/**
 * Persistence port for processing records (Specification §16). Unique key:
 * {@link DocumentId} + {@link ProcessingOperation}.
 *
 * <p>JDBC adapter is STAGE5-020. Concurrent duplicate insert must be treated as already processed
 * by the adapter / guard — never as a second successful business execution.
 */
public interface ProcessingRecordPort {

    Optional<ProcessingRecord> findByDocumentIdAndOperation(
            DocumentId documentId, ProcessingOperation operation);

    /**
     * Inserts a new processing record.
     *
     * @throws DuplicateProcessingRecordException when {@code documentId + operation} already exists
     */
    void insert(ProcessingRecord record);

    boolean exists(DocumentId documentId, ProcessingOperation operation);
}
