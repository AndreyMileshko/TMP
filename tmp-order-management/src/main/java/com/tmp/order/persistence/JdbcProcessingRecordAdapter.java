package com.tmp.order.persistence;

import com.tmp.order.application.payload.DocumentId;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.processing.DuplicateProcessingRecordException;
import com.tmp.order.application.processing.ProcessingOperation;
import com.tmp.order.application.processing.ProcessingRecord;
import com.tmp.order.application.processing.ProcessingRecordPort;
import com.tmp.order.application.processing.ProcessingStatus;
import com.tmp.order.application.processing.ResultReference;
import com.tmp.order.domain.PayloadRevision;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JDBC adapter for {@link ProcessingRecordPort}. Uniqueness of {@code document_id + operation} is
 * enforced by the database; concurrent duplicate inserts become {@link
 * DuplicateProcessingRecordException} (already processed).
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate injected by the container.")
public final class JdbcProcessingRecordAdapter implements ProcessingRecordPort {

    private final JdbcTemplate jdbc;

    public JdbcProcessingRecordAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbc = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public Optional<ProcessingRecord> findByDocumentIdAndOperation(
            DocumentId documentId, ProcessingOperation operation) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(operation, "operation");
        List<ProcessingRecord> rows =
                jdbc.query(
                        """
                        SELECT document_id, document_type_code, operation, processing_status,
                               payload_revision, processed_at, result_reference
                        FROM order_management.order_document_processing
                        WHERE document_id = ? AND operation = ?
                        """,
                        (rs, rowNum) ->
                                ProcessingRecord.rehydrate(
                                        DocumentId.of(rs.getObject("document_id", UUID.class)),
                                        DocumentTypeCode.valueOf(rs.getString("document_type_code")),
                                        ProcessingOperation.valueOf(rs.getString("operation")),
                                        ProcessingStatus.valueOf(rs.getString("processing_status")),
                                        PayloadRevision.of(rs.getLong("payload_revision")),
                                        rs.getTimestamp("processed_at").toInstant(),
                                        ResultReference.optional(rs.getString("result_reference"))
                                                .orElse(null)),
                        documentId.value(),
                        operation.name());
        return rows.stream().findFirst();
    }

    @Override
    public void insert(ProcessingRecord record) {
        Objects.requireNonNull(record, "record");
        try {
            jdbc.update(
                    """
                    INSERT INTO order_management.order_document_processing
                      (document_id, document_type_code, operation, processing_status,
                       payload_revision, processed_at, result_reference)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    record.documentId().value(),
                    record.documentTypeCode().name(),
                    record.operation().name(),
                    record.processingStatus().name(),
                    record.payloadRevision().value(),
                    Timestamp.from(record.processedAt()),
                    record.resultReference().map(ResultReference::value).orElse(null));
        } catch (DuplicateKeyException duplicate) {
            throw new DuplicateProcessingRecordException(record.documentId(), record.operation());
        }
    }

    @Override
    public boolean exists(DocumentId documentId, ProcessingOperation operation) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(operation, "operation");
        Integer count =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM order_management.order_document_processing
                        WHERE document_id = ? AND operation = ?
                        """,
                        Integer.class,
                        documentId.value(),
                        operation.name());
        return count != null && count > 0;
    }
}
