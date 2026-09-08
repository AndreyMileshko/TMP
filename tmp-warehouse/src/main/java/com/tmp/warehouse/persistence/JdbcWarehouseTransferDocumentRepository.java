package com.tmp.warehouse.persistence;

import com.tmp.warehouse.domain.MaterialReferenceId;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.TransferDocumentOptimisticLockException;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseTransferDocument;
import com.tmp.warehouse.domain.WarehouseTransferLine;
import com.tmp.warehouse.domain.WarehouseTransferLineId;
import com.tmp.warehouse.domain.repository.WarehouseTransferDocumentRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JDBC adapter for {@code warehouse.transfer_document_payload} / {@code transfer_document_lines}.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate and Clock.")
public final class JdbcWarehouseTransferDocumentRepository
        implements WarehouseTransferDocumentRepository {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public JdbcWarehouseTransferDocumentRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void insert(WarehouseTransferDocument document) {
        Objects.requireNonNull(document, "document");
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO warehouse.transfer_document_payload (
                    document_id, source_warehouse_id, destination_warehouse_id,
                    payload_schema_version, payload_revision, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                document.documentId(),
                document.sourceWarehouseId().value(),
                document.destinationWarehouseId().value(),
                document.payloadSchemaVersion(),
                document.payloadRevision(),
                Timestamp.from(now),
                Timestamp.from(now));
        insertLines(document);
    }

    @Override
    public Optional<WarehouseTransferDocument> findByDocumentId(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId");
        return Optional.ofNullable(findByDocumentIds(List.of(documentId)).get(documentId));
    }

    @Override
    public Map<UUID, WarehouseTransferDocument> findByDocumentIds(Collection<UUID> documentIds) {
        Objects.requireNonNull(documentIds, "documentIds");
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = List.copyOf(documentIds);
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<HeaderRow> headers =
                jdbc.query(
                        """
                        SELECT document_id, source_warehouse_id, destination_warehouse_id,
                               payload_schema_version, payload_revision
                          FROM warehouse.transfer_document_payload
                         WHERE document_id IN (%s)
                        """
                                .formatted(placeholders),
                        (rs, rowNum) ->
                                new HeaderRow(
                                        (UUID) rs.getObject("document_id"),
                                        (UUID) rs.getObject("source_warehouse_id"),
                                        (UUID) rs.getObject("destination_warehouse_id"),
                                        rs.getInt("payload_schema_version"),
                                        rs.getLong("payload_revision")),
                        ids.toArray());
        if (headers.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<WarehouseTransferLine>> linesByDocument = loadLinesForDocuments(ids);
        Map<UUID, WarehouseTransferDocument> result = new HashMap<>();
        for (HeaderRow header : headers) {
            List<WarehouseTransferLine> lines =
                    linesByDocument.getOrDefault(header.documentId(), List.of());
            result.put(
                    header.documentId(),
                    WarehouseTransferDocument.of(
                            header.documentId(),
                            WarehouseId.of(header.sourceWarehouseId()),
                            WarehouseId.of(header.destinationWarehouseId()),
                            header.payloadSchemaVersion(),
                            header.payloadRevision(),
                            lines));
        }
        return Map.copyOf(result);
    }

    @Override
    public void update(WarehouseTransferDocument document, long expectedPayloadRevision) {
        Objects.requireNonNull(document, "document");
        Instant now = clock.instant();
        int updated =
                jdbc.update(
                        """
                        UPDATE warehouse.transfer_document_payload
                           SET source_warehouse_id = ?,
                               destination_warehouse_id = ?,
                               payload_schema_version = ?,
                               payload_revision = ?,
                               updated_at = ?
                         WHERE document_id = ?
                           AND payload_revision = ?
                        """,
                        document.sourceWarehouseId().value(),
                        document.destinationWarehouseId().value(),
                        document.payloadSchemaVersion(),
                        document.payloadRevision(),
                        Timestamp.from(now),
                        document.documentId(),
                        expectedPayloadRevision);
        if (updated != 1) {
            Optional<WarehouseTransferDocument> existing = findByDocumentId(document.documentId());
            if (existing.isEmpty()) {
                throw new IllegalArgumentException(
                        "Transfer document payload not found: " + document.documentId());
            }
            throw new TransferDocumentOptimisticLockException(
                    document.documentId(),
                    expectedPayloadRevision,
                    existing.orElseThrow().payloadRevision());
        }
        jdbc.update(
                "DELETE FROM warehouse.transfer_document_lines WHERE document_id = ?",
                document.documentId());
        insertLines(document);
    }

    @Override
    public void deleteByDocumentId(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId");
        jdbc.update(
                "DELETE FROM warehouse.transfer_document_payload WHERE document_id = ?",
                documentId);
    }

    @Override
    public boolean existsByDocumentId(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId");
        Boolean exists =
                jdbc.queryForObject(
                        """
                        SELECT EXISTS (
                            SELECT 1 FROM warehouse.transfer_document_payload
                             WHERE document_id = ?)
                        """,
                        Boolean.class,
                        documentId);
        return Boolean.TRUE.equals(exists);
    }

    private void insertLines(WarehouseTransferDocument document) {
        for (WarehouseTransferLine line : document.orderedLines()) {
            jdbc.update(
                    """
                    INSERT INTO warehouse.transfer_document_lines (
                        id, document_id, material_reference_id, quantity, line_order)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    line.id().value(),
                    document.documentId(),
                    line.materialReferenceId().value(),
                    line.quantity().value(),
                    line.lineOrder());
        }
    }

    private Map<UUID, List<WarehouseTransferLine>> loadLinesForDocuments(List<UUID> documentIds) {
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(documentIds.size(), "?"));
        List<LineRow> rows =
                jdbc.query(
                        """
                        SELECT id, document_id, material_reference_id, quantity, line_order
                          FROM warehouse.transfer_document_lines
                         WHERE document_id IN (%s)
                         ORDER BY document_id, line_order
                        """
                                .formatted(placeholders),
                        (rs, rowNum) ->
                                new LineRow(
                                        (UUID) rs.getObject("document_id"),
                                        WarehouseTransferLine.of(
                                                WarehouseTransferLineId.of(
                                                        (UUID) rs.getObject("id")),
                                                MaterialReferenceId.of(
                                                        (UUID)
                                                                rs.getObject(
                                                                        "material_reference_id")),
                                                StockQuantity.of(rs.getBigDecimal("quantity")),
                                                rs.getInt("line_order"))),
                        documentIds.toArray());
        return rows.stream()
                .collect(
                        Collectors.groupingBy(
                                LineRow::documentId,
                                Collectors.mapping(
                                        LineRow::line,
                                        Collectors.toCollection(ArrayList::new))));
    }

    private record LineRow(UUID documentId, WarehouseTransferLine line) {}

    private record HeaderRow(
            UUID documentId,
            UUID sourceWarehouseId,
            UUID destinationWarehouseId,
            int payloadSchemaVersion,
            long payloadRevision) {}
}
