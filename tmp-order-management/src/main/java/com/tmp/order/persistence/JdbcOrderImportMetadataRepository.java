package com.tmp.order.persistence;

import com.tmp.order.api.OrderId;
import com.tmp.order.application.imports.OrderImportMetadata;
import com.tmp.order.application.imports.OrderImportMetadataRepository;
import com.tmp.security.api.UserId;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JDBC adapter for {@link OrderImportMetadataRepository}. Only this adapter may write the import
 * metadata table directly.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate injected by the container.")
public final class JdbcOrderImportMetadataRepository implements OrderImportMetadataRepository {

    private static final String EXISTS_BY_SOURCE_CHECKSUM =
            """
            SELECT COUNT(*)
            FROM order_management.order_import_metadata
            WHERE source_type = ? AND content_checksum = ?
            """;

    private static final String SELECT_BY_SOURCE_CHECKSUM =
            """
            SELECT import_id, source_type, source_reference, content_checksum,
                   imported_at, imported_by, order_id
            FROM order_management.order_import_metadata
            WHERE source_type = ? AND content_checksum = ?
            """;

    private static final String INSERT =
            """
            INSERT INTO order_management.order_import_metadata (
                import_id, source_type, source_reference, content_checksum,
                imported_at, imported_by, order_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;

    public JdbcOrderImportMetadataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public boolean existsBySourceTypeAndChecksum(String sourceType, String contentChecksum) {
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(contentChecksum, "contentChecksum");
        Integer count =
                jdbc.queryForObject(
                        EXISTS_BY_SOURCE_CHECKSUM, Integer.class, sourceType, contentChecksum);
        return count != null && count > 0;
    }

    @Override
    public Optional<OrderImportMetadata> findBySourceTypeAndChecksum(
            String sourceType, String contentChecksum) {
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(contentChecksum, "contentChecksum");
        List<OrderImportMetadata> rows =
                jdbc.query(
                        SELECT_BY_SOURCE_CHECKSUM,
                        (rs, rowNum) -> mapRow(rs),
                        sourceType,
                        contentChecksum);
        return rows.stream().findFirst();
    }

    @Override
    public OrderImportMetadata save(OrderImportMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        try {
            jdbc.update(
                    INSERT,
                    metadata.importId(),
                    metadata.sourceType(),
                    metadata.sourceReference(),
                    metadata.contentChecksum(),
                    Timestamp.from(metadata.importedAt()),
                    metadata.importedBy().value(),
                    metadata.orderId().value());
            return metadata;
        } catch (DuplicateKeyException duplicate) {
            throw duplicate;
        }
    }

    private static OrderImportMetadata mapRow(ResultSet rs) throws SQLException {
        UUID importId = rs.getObject("import_id", UUID.class);
        String sourceType = rs.getString("source_type");
        String sourceReference = rs.getString("source_reference");
        String contentChecksum = rs.getString("content_checksum");
        Instant importedAt = rs.getTimestamp("imported_at").toInstant();
        UUID importedBy = rs.getObject("imported_by", UUID.class);
        UUID orderId = rs.getObject("order_id", UUID.class);
        return OrderImportMetadata.of(
                importId,
                sourceType,
                sourceReference,
                contentChecksum,
                importedAt,
                UserId.of(importedBy),
                OrderId.of(orderId));
    }
}
