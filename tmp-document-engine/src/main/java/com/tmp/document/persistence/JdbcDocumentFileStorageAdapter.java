package com.tmp.document.persistence;

import com.tmp.document.api.port.DocumentFileReference;
import com.tmp.document.api.port.DocumentFileStoragePort;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Constructor stores Spring-managed JdbcTemplate injected by the container.")
public final class JdbcDocumentFileStorageAdapter implements DocumentFileStoragePort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDocumentFileStorageAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public DocumentFileReference registerReference(DocumentFileReference reference) {
        jdbcTemplate.update(
                """
                INSERT INTO documents.document_files (
                    id, document_id, path, mime_type, size_bytes, checksum, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                reference.id(),
                reference.documentId(),
                reference.path(),
                reference.mimeType(),
                reference.sizeBytes(),
                reference.checksum(),
                Timestamp.from(reference.createdAt()));
        return reference;
    }

    @Override
    public List<DocumentFileReference> findByDocumentId(UUID documentId) {
        return jdbcTemplate.query(
                """
                SELECT id, document_id, path, mime_type, size_bytes, checksum, created_at
                FROM documents.document_files
                WHERE document_id = ?
                ORDER BY created_at ASC
                """,
                (rs, rowNum) -> new DocumentFileReference(
                        rs.getObject("id", UUID.class),
                        rs.getObject("document_id", UUID.class),
                        rs.getString("path"),
                        rs.getString("mime_type"),
                        rs.getLong("size_bytes"),
                        rs.getString("checksum"),
                        rs.getTimestamp("created_at").toInstant()),
                documentId);
    }
}
