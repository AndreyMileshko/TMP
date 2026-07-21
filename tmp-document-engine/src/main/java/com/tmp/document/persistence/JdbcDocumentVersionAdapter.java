package com.tmp.document.persistence;

import com.tmp.document.api.port.DocumentVersionPort;
import com.tmp.document.api.port.DocumentVersionSnapshot;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Constructor stores Spring-managed JdbcTemplate injected by the container.")
public final class JdbcDocumentVersionAdapter implements DocumentVersionPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDocumentVersionAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveSnapshot(DocumentVersionSnapshot snapshot) {
        jdbcTemplate.update(
                """
                INSERT INTO documents.document_versions (
                    id, document_id, version_number, title, changed_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                snapshot.id(),
                snapshot.documentId(),
                snapshot.versionNumber(),
                snapshot.title(),
                Timestamp.from(snapshot.changedAt()));
    }

    @Override
    public List<DocumentVersionSnapshot> findByDocumentId(UUID documentId) {
        return jdbcTemplate.query(
                """
                SELECT id, document_id, version_number, title, changed_at
                FROM documents.document_versions
                WHERE document_id = ?
                ORDER BY version_number ASC
                """,
                (rs, rowNum) -> new DocumentVersionSnapshot(
                        rs.getObject("id", UUID.class),
                        rs.getObject("document_id", UUID.class),
                        rs.getLong("version_number"),
                        rs.getString("title"),
                        rs.getTimestamp("changed_at").toInstant()),
                documentId);
    }
}
