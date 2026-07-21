package com.tmp.document.persistence;

import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentQuery;
import com.tmp.document.api.DocumentStatus;
import com.tmp.document.api.DocumentTypeDescriptor;
import com.tmp.document.api.port.DocumentStoragePort;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Constructor stores Spring-managed JdbcTemplate injected by the container.")
public final class JdbcDocumentStorageAdapter implements DocumentStoragePort {

    private static final RowMapper<DocumentMetadata> ROW_MAPPER = JdbcDocumentStorageAdapter::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcDocumentStorageAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public DocumentMetadata insert(DocumentMetadata document) {
        jdbcTemplate.update(
                """
                INSERT INTO documents.documents (
                    id, document_type_id, document_number, title, status, version,
                    created_at, updated_at, posted_at, closed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                document.id(),
                document.documentTypeId(),
                document.documentNumber(),
                document.title(),
                document.status().name(),
                document.version(),
                Timestamp.from(document.createdAt()),
                Timestamp.from(document.updatedAt()),
                toTimestamp(document.postedAt()),
                toTimestamp(document.closedAt()));
        return document;
    }

    @Override
    public DocumentMetadata update(DocumentMetadata document, long expectedVersion) {
        int updated = jdbcTemplate.update(
                """
                UPDATE documents.documents
                SET title = ?, status = ?, version = ?, updated_at = ?, posted_at = ?, closed_at = ?
                WHERE id = ? AND version = ?
                """,
                document.title(),
                document.status().name(),
                document.version(),
                Timestamp.from(document.updatedAt()),
                toTimestamp(document.postedAt()),
                toTimestamp(document.closedAt()),
                document.id(),
                expectedVersion);
        if (updated == 0) {
            throw new IllegalStateException("Document version conflict or missing document: " + document.id());
        }
        return document;
    }

    @Override
    public void delete(UUID documentId) {
        jdbcTemplate.update("DELETE FROM documents.documents WHERE id = ?", documentId);
    }

    @Override
    public Optional<DocumentMetadata> findById(UUID documentId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT * FROM documents.documents WHERE id = ?",
                    ROW_MAPPER,
                    documentId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<DocumentMetadata> search(DocumentQuery query) {
        StringBuilder sql = new StringBuilder("SELECT * FROM documents.documents WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        query.documentTypeId().ifPresent(typeId -> {
            sql.append(" AND document_type_id = ?");
            params.add(typeId);
        });
        query.status().ifPresent(status -> {
            sql.append(" AND status = ?");
            params.add(status.name());
        });
        query.numberContains().ifPresent(numberPart -> {
            sql.append(" AND document_number ILIKE ?");
            params.add("%" + numberPart + "%");
        });
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(query.limit());
        params.add(query.offset());
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    @Override
    public long countAll() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM documents.documents", Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public List<DocumentTypeDescriptor> listDocumentTypes() {
        return jdbcTemplate.query(
                "SELECT id, display_name, description FROM documents.document_types ORDER BY id",
                (rs, rowNum) -> new DocumentTypeDescriptor(
                        rs.getString("id"),
                        rs.getString("display_name"),
                        rs.getString("description")));
    }

    @Override
    public void registerDocumentType(String typeId, String displayName, String description) {
        if (documentTypeExists(typeId)) {
            jdbcTemplate.update(
                    """
                    UPDATE documents.document_types
                    SET display_name = ?, description = ?, version = version + 1
                    WHERE id = ?
                    """,
                    displayName,
                    description,
                    typeId);
            return;
        }
        jdbcTemplate.update(
                """
                INSERT INTO documents.document_types (id, display_name, description, registered_at, version)
                VALUES (?, ?, ?, ?, 0)
                """,
                typeId,
                displayName,
                description,
                Timestamp.from(Instant.now()));
    }

    @Override
    public boolean documentTypeExists(String typeId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM documents.document_types WHERE id = ?)",
                Boolean.class,
                typeId);
        return Boolean.TRUE.equals(exists);
    }

    private static DocumentMetadata mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new DocumentMetadata(
                rs.getObject("id", UUID.class),
                rs.getString("document_type_id"),
                rs.getString("document_number"),
                rs.getString("title"),
                DocumentStatus.valueOf(rs.getString("status")),
                rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                toInstant(rs.getTimestamp("posted_at")),
                toInstant(rs.getTimestamp("closed_at")));
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
