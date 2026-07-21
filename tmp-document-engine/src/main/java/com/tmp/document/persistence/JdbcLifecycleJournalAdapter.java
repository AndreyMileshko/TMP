package com.tmp.document.persistence;

import com.tmp.document.api.DocumentStatus;
import com.tmp.document.api.LifecycleEventType;
import com.tmp.document.api.LifecycleJournalEntry;
import com.tmp.document.api.port.LifecycleJournalPort;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Constructor stores Spring-managed JdbcTemplate injected by the container.")
public final class JdbcLifecycleJournalAdapter implements LifecycleJournalPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcLifecycleJournalAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public LifecycleJournalEntry append(
            UUID documentId,
            LifecycleEventType eventType,
            DocumentStatus fromStatus,
            DocumentStatus toStatus,
            String details) {
        LifecycleJournalEntry entry = new LifecycleJournalEntry(
                UUID.randomUUID(),
                documentId,
                eventType,
                fromStatus,
                toStatus,
                Instant.now(),
                details == null ? "" : details);
        jdbcTemplate.update(
                """
                INSERT INTO documents.document_lifecycle_journal (
                    id, document_id, event_type, from_status, to_status, occurred_at, details)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                entry.id(),
                entry.documentId(),
                entry.eventType().name(),
                entry.fromStatus() == null ? null : entry.fromStatus().name(),
                entry.toStatus().name(),
                Timestamp.from(entry.occurredAt()),
                entry.details());
        return entry;
    }

    @Override
    public List<LifecycleJournalEntry> findByDocumentId(UUID documentId) {
        return jdbcTemplate.query(
                """
                SELECT id, document_id, event_type, from_status, to_status, occurred_at, details
                FROM documents.document_lifecycle_journal
                WHERE document_id = ?
                ORDER BY occurred_at ASC
                """,
                (rs, rowNum) -> new LifecycleJournalEntry(
                        rs.getObject("id", UUID.class),
                        rs.getObject("document_id", UUID.class),
                        LifecycleEventType.valueOf(rs.getString("event_type")),
                        rs.getString("from_status") == null
                                ? null
                                : DocumentStatus.valueOf(rs.getString("from_status")),
                        DocumentStatus.valueOf(rs.getString("to_status")),
                        rs.getTimestamp("occurred_at").toInstant(),
                        rs.getString("details")),
                documentId);
    }
}
