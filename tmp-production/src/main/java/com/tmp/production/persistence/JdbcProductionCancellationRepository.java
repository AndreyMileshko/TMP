package com.tmp.production.persistence;

import com.tmp.production.domain.CancellationItemAction;
import com.tmp.production.domain.ProductionCancellation;
import com.tmp.production.domain.ProductionCancellationImmutableException;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.ProductionStatus;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.domain.repository.ProductionCancellationQuery;
import com.tmp.production.domain.repository.ProductionCancellationRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JDBC adapter for Production Cancellation payload tables
 * ({@code production.production_cancellations*}).
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate and Clock injected by the container.")
public final class JdbcProductionCancellationRepository
        implements ProductionCancellationRepository, ProductionCancellationQuery {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public JdbcProductionCancellationRepository(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ProductionCancellation saveDraft(ProductionCancellation cancellation) {
        Objects.requireNonNull(cancellation, "cancellation");
        if (cancellation.posted()) {
            throw new IllegalArgumentException("saveDraft rejects posted cancellation payload");
        }
        Optional<HeaderRow> existing = findHeader(cancellation.documentId());
        if (existing.isPresent() && existing.orElseThrow().posted()) {
            throw new ProductionCancellationImmutableException(cancellation.documentId());
        }
        Instant now = clock.instant();
        if (existing.isEmpty()) {
            insertHeader(cancellation, now);
        } else {
            updateHeaderDraft(cancellation, now);
            deleteItemLines(cancellation.documentId());
        }
        insertItemLines(cancellation);
        return findByDocumentId(cancellation.documentId()).orElseThrow();
    }

    @Override
    public ProductionCancellation markPosted(ProductionCancellation cancellation) {
        Objects.requireNonNull(cancellation, "cancellation");
        if (!cancellation.posted()) {
            throw new IllegalArgumentException("markPosted requires posted=true domain state");
        }
        Instant now = clock.instant();
        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE production.production_cancellations
                           SET posted = TRUE, updated_at = ?
                         WHERE document_id = ? AND posted = FALSE
                        """,
                        Timestamp.from(now),
                        cancellation.documentId());
        if (updated != 1) {
            Optional<HeaderRow> existing = findHeader(cancellation.documentId());
            if (existing.isPresent() && existing.orElseThrow().posted()) {
                throw new ProductionCancellationImmutableException(cancellation.documentId());
            }
            throw new IllegalStateException(
                    "Production Cancellation not found for markPosted: "
                            + cancellation.documentId());
        }
        return findByDocumentId(cancellation.documentId()).orElseThrow();
    }

    @Override
    public Optional<ProductionCancellation> findByDocumentId(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId");
        Optional<HeaderRow> header = findHeader(documentId);
        if (header.isEmpty()) {
            return Optional.empty();
        }
        HeaderRow row = header.orElseThrow();
        List<ProductionCancellation.ItemLine> itemLines = loadItemLines(documentId);
        return Optional.of(
                ProductionCancellation.restore(
                        row.documentId(),
                        SourceOrderId.of(row.sourceOrderId()),
                        row.cancelledAt(),
                        row.reason(),
                        itemLines,
                        row.posted()));
    }

    @Override
    public boolean hasPostedCancellation(SourceOrderId sourceOrderId) {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        Boolean posted =
                jdbcTemplate.queryForObject(
                        """
                        SELECT EXISTS (
                            SELECT 1
                              FROM production.production_cancellations
                             WHERE source_order_id = ?
                               AND posted = TRUE
                        )
                        """,
                        Boolean.class,
                        sourceOrderId.value());
        return Boolean.TRUE.equals(posted);
    }

    @Override
    public Set<SourceOrderId> findPostedCancellationOrderIds(Collection<SourceOrderId> sourceOrderIds) {
        Objects.requireNonNull(sourceOrderIds, "sourceOrderIds");
        if (sourceOrderIds.isEmpty()) {
            return Set.of();
        }
        List<UUID> ids = sourceOrderIds.stream().map(SourceOrderId::value).distinct().toList();
        Set<SourceOrderId> posted = new LinkedHashSet<>();
        int chunkSize = 500;
        for (int start = 0; start < ids.size(); start += chunkSize) {
            int end = Math.min(start + chunkSize, ids.size());
            List<UUID> chunk = ids.subList(start, end);
            String placeholders = String.join(",", java.util.Collections.nCopies(chunk.size(), "?"));
            String sql =
                    """
                    SELECT DISTINCT source_order_id
                      FROM production.production_cancellations
                     WHERE posted = TRUE
                       AND source_order_id IN (
                    """
                            + placeholders
                            + ")";
            posted.addAll(
                    jdbcTemplate.query(
                            sql,
                            (rs, rowNum) -> SourceOrderId.of(rs.getObject("source_order_id", UUID.class)),
                            chunk.toArray()));
        }
        return Set.copyOf(posted);
    }

    private Optional<HeaderRow> findHeader(UUID documentId) {
        try {
            return Optional.of(
                    jdbcTemplate.queryForObject(
                            """
                            SELECT document_id, source_order_id, cancelled_at, reason_text, posted
                              FROM production.production_cancellations
                             WHERE document_id = ?
                            """,
                            (rs, rowNum) ->
                                    new HeaderRow(
                                            rs.getObject("document_id", UUID.class),
                                            rs.getObject("source_order_id", UUID.class),
                                            rs.getTimestamp("cancelled_at").toInstant(),
                                            Optional.ofNullable(rs.getString("reason_text")),
                                            rs.getBoolean("posted")),
                            documentId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private void insertHeader(ProductionCancellation cancellation, Instant now) {
        jdbcTemplate.update(
                """
                INSERT INTO production.production_cancellations
                    (document_id, source_order_id, cancelled_at, reason_text, posted,
                     created_at, updated_at)
                VALUES (?, ?, ?, ?, FALSE, ?, ?)
                """,
                cancellation.documentId(),
                cancellation.sourceOrderId().value(),
                Timestamp.from(cancellation.cancelledAt()),
                cancellation.reason().orElse(null),
                Timestamp.from(now),
                Timestamp.from(now));
    }

    private void updateHeaderDraft(ProductionCancellation cancellation, Instant now) {
        jdbcTemplate.update(
                """
                UPDATE production.production_cancellations
                   SET source_order_id = ?, cancelled_at = ?, reason_text = ?, updated_at = ?
                 WHERE document_id = ? AND posted = FALSE
                """,
                cancellation.sourceOrderId().value(),
                Timestamp.from(cancellation.cancelledAt()),
                cancellation.reason().orElse(null),
                Timestamp.from(now),
                cancellation.documentId());
    }

    private void deleteItemLines(UUID documentId) {
        jdbcTemplate.update(
                "DELETE FROM production.production_cancellation_item_lines WHERE document_id = ?",
                documentId);
    }

    private void insertItemLines(ProductionCancellation cancellation) {
        int order = 0;
        for (ProductionCancellation.ItemLine line : cancellation.itemLines()) {
            jdbcTemplate.update(
                    """
                    INSERT INTO production.production_cancellation_item_lines
                        (id, document_id, source_order_item_id, specification_id, previous_status,
                         action, active_quantity_cancelled, released_quantity_preserved, line_order)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(),
                    cancellation.documentId(),
                    line.sourceOrderItemId().value(),
                    line.specificationId().value(),
                    line.previousStatus().name(),
                    line.action().name(),
                    line.activeQuantityCancelled().value(),
                    line.releasedQuantityPreserved().value(),
                    order++);
        }
    }

    private List<ProductionCancellation.ItemLine> loadItemLines(UUID documentId) {
        return jdbcTemplate.query(
                """
                SELECT source_order_item_id, specification_id, previous_status, action,
                       active_quantity_cancelled, released_quantity_preserved
                  FROM production.production_cancellation_item_lines
                 WHERE document_id = ?
                 ORDER BY line_order
                """,
                (rs, rowNum) ->
                        new ProductionCancellation.ItemLine(
                                SourceOrderItemId.of(
                                        rs.getObject("source_order_item_id", UUID.class)),
                                SpecificationId.of(rs.getObject("specification_id", UUID.class)),
                                ProductionStatus.valueOf(rs.getString("previous_status")),
                                CancellationItemAction.valueOf(rs.getString("action")),
                                ProductionQuantity.nonNegative(
                                        rs.getBigDecimal("active_quantity_cancelled")),
                                ProductionQuantity.nonNegative(
                                        rs.getBigDecimal("released_quantity_preserved"))),
                documentId);
    }

    private record HeaderRow(
            UUID documentId,
            UUID sourceOrderId,
            Instant cancelledAt,
            Optional<String> reason,
            boolean posted) {}
}
