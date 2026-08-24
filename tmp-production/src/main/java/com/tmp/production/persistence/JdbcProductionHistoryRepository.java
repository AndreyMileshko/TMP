package com.tmp.production.persistence;

import com.tmp.production.domain.ProductionHistoryEntry;
import com.tmp.production.domain.ProductionHistoryEntry.ProductionHistoryEntryId;
import com.tmp.production.domain.ProductionHistoryEntry.ProductionHistoryType;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.repository.ProductionHistoryRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * JDBC append-only adapter for {@code production.production_history}.
 *
 * <p>No UPDATE/DELETE SQL. DB triggers additionally reject mutations.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate injected by the container.")
public final class JdbcProductionHistoryRepository implements ProductionHistoryRepository {

    private static final RowMapper<ProductionHistoryEntry> ROW_MAPPER =
            JdbcProductionHistoryRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcProductionHistoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public ProductionHistoryEntry append(ProductionHistoryEntry entry) {
        Objects.requireNonNull(entry, "entry");
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO production.production_history (
                        entry_id,
                        source_order_id,
                        history_type,
                        occurred_at,
                        recorded_at,
                        source_order_item_id,
                        source_document_id,
                        business_reference_id,
                        actor_ref,
                        summary,
                        details_json)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                    """,
                    entry.entryId().value(),
                    entry.sourceOrderId().value(),
                    entry.historyType().name(),
                    Timestamp.from(entry.occurredAt()),
                    Timestamp.from(entry.recordedAt()),
                    entry.sourceOrderItemId().map(SourceOrderItemId::value).orElse(null),
                    entry.sourceDocumentId().orElse(null),
                    entry.businessReferenceId().orElse(null),
                    entry.actorRef().orElse(null),
                    entry.summary().orElse(null),
                    entry.detailsJson().orElse(null));
        } catch (DuplicateKeyException ex) {
            throw new ProductionPersistenceException(
                    "Duplicate Production history fact for type="
                            + entry.historyType()
                            + " businessReferenceId="
                            + entry.businessReferenceId().orElse(null),
                    ex);
        }
        return entry;
    }

    @Override
    public List<ProductionHistoryEntry> listByOrder(SourceOrderId sourceOrderId) {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        List<ProductionHistoryEntry> rows =
                jdbcTemplate.query(
                        """
                        SELECT entry_id,
                               source_order_id,
                               history_type,
                               occurred_at,
                               recorded_at,
                               source_order_item_id,
                               source_document_id,
                               business_reference_id,
                               actor_ref,
                               summary,
                               details_json::text AS details_json
                          FROM production.production_history
                         WHERE source_order_id = ?
                         ORDER BY occurred_at ASC, recorded_at ASC, entry_id ASC
                        """,
                        ROW_MAPPER,
                        sourceOrderId.value());
        return List.copyOf(rows);
    }

    private static ProductionHistoryEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
        UUID itemId = rs.getObject("source_order_item_id", UUID.class);
        UUID documentId = rs.getObject("source_document_id", UUID.class);
        UUID businessReferenceId = rs.getObject("business_reference_id", UUID.class);
        String actorRef = rs.getString("actor_ref");
        String summary = rs.getString("summary");
        String detailsJson = rs.getString("details_json");
        return ProductionHistoryEntry.restore(
                ProductionHistoryEntryId.of(rs.getObject("entry_id", UUID.class)),
                SourceOrderId.of(rs.getObject("source_order_id", UUID.class)),
                ProductionHistoryType.valueOf(rs.getString("history_type")),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getTimestamp("recorded_at").toInstant(),
                itemId == null ? Optional.empty() : Optional.of(SourceOrderItemId.of(itemId)),
                Optional.ofNullable(documentId),
                Optional.ofNullable(businessReferenceId),
                Optional.ofNullable(actorRef),
                Optional.ofNullable(summary),
                Optional.ofNullable(detailsJson));
    }
}
