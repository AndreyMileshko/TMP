package com.tmp.warehouse.persistence;

import com.tmp.warehouse.domain.MaterialReferenceId;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.TransferContinuationReason;
import com.tmp.warehouse.domain.TransferDocumentOptimisticLockException;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseTransferDocument;
import com.tmp.warehouse.domain.WarehouseTransferLine;
import com.tmp.warehouse.domain.WarehouseTransferLineId;
import com.tmp.warehouse.domain.repository.WarehouseTransferDocumentRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
                    payload_schema_version, payload_revision,
                    continuation_of_document_id, continuation_reason,
                    created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                document.documentId(),
                document.sourceWarehouseId().value(),
                document.destinationWarehouseId().value(),
                document.payloadSchemaVersion(),
                document.payloadRevision(),
                document.continuationOfDocumentId().orElse(null),
                document.continuationReason().map(Enum::name).orElse(null),
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
    public Optional<WarehouseTransferDocument> lockByDocumentId(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId");
        List<HeaderRow> headers =
                jdbc.query(
                        """
                        SELECT document_id, source_warehouse_id, destination_warehouse_id,
                               payload_schema_version, payload_revision,
                               continuation_of_document_id, continuation_reason
                          FROM warehouse.transfer_document_payload
                         WHERE document_id = ?
                         FOR UPDATE
                        """,
                        (rs, rowNum) -> mapHeader(rs),
                        documentId);
        if (headers.isEmpty()) {
            return Optional.empty();
        }
        HeaderRow header = headers.get(0);
        List<WarehouseTransferLine> lines =
                loadLinesForDocuments(List.of(documentId))
                        .getOrDefault(documentId, List.of());
        return Optional.of(toDocument(header, lines));
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
                        ("SELECT document_id, source_warehouse_id, destination_warehouse_id, "
                                        + "payload_schema_version, payload_revision, "
                                        + "continuation_of_document_id, continuation_reason "
                                        + "FROM warehouse.transfer_document_payload "
                                        + "WHERE document_id IN (%s)")
                                .formatted(placeholders),
                        (rs, rowNum) -> mapHeader(rs),
                        ids.toArray());
        if (headers.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<WarehouseTransferLine>> linesByDocument = loadLinesForDocuments(ids);
        Map<UUID, WarehouseTransferDocument> result = new HashMap<>();
        for (HeaderRow header : headers) {
            List<WarehouseTransferLine> lines =
                    linesByDocument.getOrDefault(header.documentId(), List.of());
            result.put(header.documentId(), toDocument(header, lines));
        }
        return Map.copyOf(result);
    }

    @Override
    public void update(WarehouseTransferDocument document, long expectedPayloadRevision) {
        Objects.requireNonNull(document, "document");
        Instant now = clock.instant();
        // Lineage columns are intentionally omitted from SET — ordinary DRAFT updates must
        // preserve immutable continuation metadata (Stage 3.5.7).
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
        // Demand transfer links FK transfer_line_id. Payload updates rewrite lines via
        // delete+insert (including shortfall shrink that keeps the same line ids). Park and
        // restore links for line ids that survive so Stage 3.5.7 send remains compatible.
        List<DemandLinkRow> parkedLinks = parkDemandTransferLinks(document.documentId());
        jdbc.update(
                "DELETE FROM warehouse.transfer_document_lines WHERE document_id = ?",
                document.documentId());
        insertLines(document);
        restoreDemandTransferLinks(parkedLinks, survivingTransferLineIds(document));
    }

    private List<DemandLinkRow> parkDemandTransferLinks(UUID documentId) {
        List<DemandLinkRow> links =
                jdbc.query(
                        """
                        SELECT l.id, l.demand_line_id, l.transfer_document_id, l.transfer_line_id,
                               l.linked_quantity
                          FROM warehouse.warehouse_demand_transfer_links l
                          JOIN warehouse.transfer_document_lines tl
                            ON tl.id = l.transfer_line_id
                         WHERE tl.document_id = ?
                        """,
                        (rs, rowNum) ->
                                new DemandLinkRow(
                                        (UUID) rs.getObject("id"),
                                        (UUID) rs.getObject("demand_line_id"),
                                        (UUID) rs.getObject("transfer_document_id"),
                                        (UUID) rs.getObject("transfer_line_id"),
                                        rs.getBigDecimal("linked_quantity")),
                        documentId);
        if (!links.isEmpty()) {
            jdbc.update(
                    """
                    DELETE FROM warehouse.warehouse_demand_transfer_links
                     WHERE transfer_line_id IN (
                        SELECT id FROM warehouse.transfer_document_lines WHERE document_id = ?)
                    """,
                    documentId);
        }
        return links;
    }

    private void restoreDemandTransferLinks(
            List<DemandLinkRow> parkedLinks, java.util.Set<UUID> survivingLineIds) {
        for (DemandLinkRow link : parkedLinks) {
            if (!survivingLineIds.contains(link.transferLineId())) {
                continue;
            }
            jdbc.update(
                    """
                    INSERT INTO warehouse.warehouse_demand_transfer_links (
                        id, demand_line_id, transfer_document_id, transfer_line_id, linked_quantity)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    link.id(),
                    link.demandLineId(),
                    link.transferDocumentId(),
                    link.transferLineId(),
                    link.linkedQuantity());
        }
    }

    private static java.util.Set<UUID> survivingTransferLineIds(WarehouseTransferDocument document) {
        return document.orderedLines().stream()
                .map(line -> line.id().value())
                .collect(Collectors.toSet());
    }

    private record DemandLinkRow(
            UUID id,
            UUID demandLineId,
            UUID transferDocumentId,
            UUID transferLineId,
            java.math.BigDecimal linkedQuantity) {}


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
                        ("SELECT id, document_id, material_reference_id, quantity, line_order "
                                        + "FROM warehouse.transfer_document_lines "
                                        + "WHERE document_id IN (%s) "
                                        + "ORDER BY document_id, line_order")
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

    private static HeaderRow mapHeader(java.sql.ResultSet rs) throws java.sql.SQLException {
        String reasonRaw = rs.getString("continuation_reason");
        TransferContinuationReason reason =
                reasonRaw == null || reasonRaw.isBlank()
                        ? null
                        : TransferContinuationReason.parse(reasonRaw);
        return new HeaderRow(
                (UUID) rs.getObject("document_id"),
                (UUID) rs.getObject("source_warehouse_id"),
                (UUID) rs.getObject("destination_warehouse_id"),
                rs.getInt("payload_schema_version"),
                rs.getLong("payload_revision"),
                (UUID) rs.getObject("continuation_of_document_id"),
                reason);
    }

    private static WarehouseTransferDocument toDocument(
            HeaderRow header, List<WarehouseTransferLine> lines) {
        return WarehouseTransferDocument.of(
                header.documentId(),
                WarehouseId.of(header.sourceWarehouseId()),
                WarehouseId.of(header.destinationWarehouseId()),
                header.payloadSchemaVersion(),
                header.payloadRevision(),
                lines,
                header.continuationOfDocumentId(),
                header.continuationReason());
    }

    private record LineRow(UUID documentId, WarehouseTransferLine line) {}

    private record HeaderRow(
            UUID documentId,
            UUID sourceWarehouseId,
            UUID destinationWarehouseId,
            int payloadSchemaVersion,
            long payloadRevision,
            UUID continuationOfDocumentId,
            TransferContinuationReason continuationReason) {}
}
