package com.tmp.reference.persistence;

import com.tmp.reference.domain.ColorId;
import com.tmp.reference.domain.So7ColorMapping;
import com.tmp.reference.domain.So7ColorMappingRepository;
import com.tmp.reference.domain.So7SourceKey;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * JDBC adapter for {@code reference.so7_color_mappings} (TMP-SPEC-015 section 6 / COL-021A).
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate injected by the container.")
public final class JdbcSo7ColorMappingRepository implements So7ColorMappingRepository {

    private static final RowMapper<So7ColorMapping> ROW_MAPPER =
            JdbcSo7ColorMappingRepository::mapRow;

    private static final String SELECT_COLUMNS =
            """
            id, source_text, source_key, inside_color_id, outside_color_id,
            active, version, created_at, updated_at, created_by, updated_by
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcSo7ColorMappingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public So7ColorMapping insert(So7ColorMapping mapping) {
        Objects.requireNonNull(mapping, "mapping");
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO reference.so7_color_mappings (
                        id, source_text, source_key, inside_color_id, outside_color_id,
                        active, version, created_at, updated_at, created_by, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    mapping.id(),
                    mapping.sourceText(),
                    mapping.sourceKey().value(),
                    mapping.insideColorId().value(),
                    mapping.outsideColorId().value(),
                    mapping.active(),
                    mapping.version(),
                    Timestamp.from(mapping.createdAt()),
                    Timestamp.from(mapping.updatedAt()),
                    mapping.createdBy(),
                    mapping.updatedBy());
            return mapping;
        } catch (DuplicateKeyException ex) {
            throw new So7ColorMapping.DuplicateSourceKeyException(
                    "SO7 mapping already exists for source key: " + mapping.sourceKey().value(),
                    ex);
        } catch (DataIntegrityViolationException ex) {
            throw translateIntegrity(ex, mapping);
        }
    }

    @Override
    public So7ColorMapping update(So7ColorMapping mapping) {
        Objects.requireNonNull(mapping, "mapping");
        long expectedVersion = mapping.version();
        long nextVersion = expectedVersion + 1;
        int updated;
        try {
            updated =
                    jdbcTemplate.update(
                            """
                            UPDATE reference.so7_color_mappings
                            SET inside_color_id = ?, outside_color_id = ?, active = ?,
                                version = ?, updated_at = ?, updated_by = ?
                            WHERE id = ? AND version = ?
                            """,
                            mapping.insideColorId().value(),
                            mapping.outsideColorId().value(),
                            mapping.active(),
                            nextVersion,
                            Timestamp.from(mapping.updatedAt()),
                            mapping.updatedBy(),
                            mapping.id(),
                            expectedVersion);
        } catch (DataIntegrityViolationException ex) {
            throw translateIntegrity(ex, mapping);
        }
        if (updated == 0) {
            throw new So7ColorMapping.OptimisticLockConflictException(
                    "SO7 mapping version conflict: " + mapping.id());
        }
        return So7ColorMapping.rehydrate(
                mapping.id(),
                mapping.sourceText(),
                mapping.sourceKey(),
                mapping.insideColorId(),
                mapping.outsideColorId(),
                mapping.active(),
                nextVersion,
                mapping.createdAt(),
                mapping.updatedAt(),
                mapping.createdBy(),
                mapping.updatedBy());
    }

    @Override
    public Optional<So7ColorMapping> findById(UUID id) {
        Objects.requireNonNull(id, "id");
        try {
            return Optional.ofNullable(
                    jdbcTemplate.queryForObject(
                            "SELECT "
                                    + SELECT_COLUMNS
                                    + " FROM reference.so7_color_mappings WHERE id = ?",
                            ROW_MAPPER,
                            id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<So7ColorMapping> findBySourceKey(So7SourceKey sourceKey) {
        Objects.requireNonNull(sourceKey, "sourceKey");
        try {
            return Optional.ofNullable(
                    jdbcTemplate.queryForObject(
                            "SELECT "
                                    + SELECT_COLUMNS
                                    + " FROM reference.so7_color_mappings WHERE source_key = ?",
                            ROW_MAPPER,
                            sourceKey.value()));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<So7ColorMapping> search(Boolean activeFilter, String searchText) {
        String normalizedSearch = searchText == null ? "" : searchText.trim();
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT DISTINCT
                            m.id, m.source_text, m.source_key, m.inside_color_id, m.outside_color_id,
                            m.active, m.version, m.created_at, m.updated_at, m.created_by, m.updated_by
                        FROM reference.so7_color_mappings m
                        JOIN reference.colors ci ON ci.id = m.inside_color_id
                        JOIN reference.colors co ON co.id = m.outside_color_id
                        WHERE 1 = 1
                        """);
        List<Object> args = new ArrayList<>();
        if (activeFilter != null) {
            sql.append(" AND m.active = ?");
            args.add(activeFilter);
        }
        if (!normalizedSearch.isEmpty()) {
            sql.append(
                    """
                     AND (m.source_text ILIKE ? ESCAPE '\\'
                          OR ci.code ILIKE ? ESCAPE '\\'
                          OR co.code ILIKE ? ESCAPE '\\')
                    """);
            String pattern = "%" + escapeLike(normalizedSearch) + "%";
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        sql.append(" ORDER BY m.source_text");
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    @Override
    public int deactivateByColorId(ColorId colorId, UUID updatedBy, Instant updatedAt) {
        Objects.requireNonNull(colorId, "colorId");
        Objects.requireNonNull(updatedAt, "updatedAt");
        return jdbcTemplate.update(
                """
                UPDATE reference.so7_color_mappings
                SET active = FALSE,
                    version = version + 1,
                    updated_at = ?,
                    updated_by = ?
                WHERE active = TRUE
                  AND (inside_color_id = ? OR outside_color_id = ?)
                """,
                Timestamp.from(updatedAt),
                updatedBy,
                colorId.value(),
                colorId.value());
    }

    @Override
    public long countActiveByColorId(ColorId colorId) {
        Objects.requireNonNull(colorId, "colorId");
        Long count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM reference.so7_color_mappings
                        WHERE active = TRUE
                          AND (inside_color_id = ? OR outside_color_id = ?)
                        """,
                        Long.class,
                        colorId.value(),
                        colorId.value());
        return count == null ? 0L : count;
    }

    @Override
    public void delete(UUID id, long expectedVersion) {
        Objects.requireNonNull(id, "id");
        int deleted =
                jdbcTemplate.update(
                        """
                        DELETE FROM reference.so7_color_mappings
                        WHERE id = ? AND version = ?
                        """,
                        id,
                        expectedVersion);
        if (deleted == 0) {
            throw new So7ColorMapping.OptimisticLockConflictException(
                    "SO7 mapping version conflict on delete: " + id);
        }
    }

    private static RuntimeException translateIntegrity(
            DataIntegrityViolationException ex, So7ColorMapping mapping) {
        String message = String.valueOf(ex.getMostSpecificCause().getMessage());
        if (message.contains("uk_so7_color_mappings_source_key")
                || message.contains("source_key")) {
            return new So7ColorMapping.DuplicateSourceKeyException(
                    "SO7 mapping already exists for source key: " + mapping.sourceKey().value(),
                    ex);
        }
        if (message.contains("fk_so7_color_mappings_inside_color")
                || message.contains("fk_so7_color_mappings_outside_color")
                || message.contains("inside_color_id")
                || message.contains("outside_color_id")) {
            return new IllegalArgumentException(
                    "SO7 mapping references unknown catalog color; inside="
                            + mapping.insideColorId()
                            + ", outside="
                            + mapping.outsideColorId(),
                    ex);
        }
        return ex;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static So7ColorMapping mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        String sourceText = rs.getString("source_text");
        String persistedKey = rs.getString("source_key");
        So7SourceKey sourceKey = So7SourceKey.of(sourceText);
        if (!sourceKey.value().equals(persistedKey)) {
            throw new IllegalStateException(
                    "Persisted source_key does not match So7SourceKey.of(source_text); sourceText="
                            + sourceText
                            + ", persisted="
                            + persistedKey
                            + ", expected="
                            + sourceKey.value());
        }
        return So7ColorMapping.rehydrate(
                rs.getObject("id", UUID.class),
                sourceText,
                sourceKey,
                ColorId.of(rs.getObject("inside_color_id", UUID.class)),
                ColorId.of(rs.getObject("outside_color_id", UUID.class)),
                rs.getBoolean("active"),
                rs.getLong("version"),
                createdAt.toInstant(),
                updatedAt.toInstant(),
                rs.getObject("created_by", UUID.class),
                rs.getObject("updated_by", UUID.class));
    }
}