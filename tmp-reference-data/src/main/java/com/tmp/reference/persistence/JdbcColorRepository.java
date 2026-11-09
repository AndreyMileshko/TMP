package com.tmp.reference.persistence;

import com.tmp.reference.domain.Color;
import com.tmp.reference.domain.ColorCodeKey;
import com.tmp.reference.domain.ColorId;
import com.tmp.reference.domain.ColorRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * JDBC adapter for {@code reference.colors}. Does not update {@code code}/{@code code_key} and has
 * no delete API (TMP-SPEC-015 section 5.3 / COL-020A).
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate injected by the container.")
public final class JdbcColorRepository implements ColorRepository {

    private static final RowMapper<Color> ROW_MAPPER = JdbcColorRepository::mapRow;

    private static final String SELECT_COLUMNS =
            """
            id, code, code_key, active, version, created_at, updated_at, created_by, updated_by
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcColorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public Color insert(Color color) {
        Objects.requireNonNull(color, "color");
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO reference.colors (
                        id, code, code_key, active, version,
                        created_at, updated_at, created_by, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    color.id().value(),
                    color.code(),
                    color.codeKey().value(),
                    color.active(),
                    color.version(),
                    Timestamp.from(color.createdAt()),
                    Timestamp.from(color.updatedAt()),
                    color.createdBy(),
                    color.updatedBy());
            return color;
        } catch (DuplicateKeyException ex) {
            throw new Color.DuplicateCodeException(
                    "Color code already exists (case-insensitive): " + color.code(), ex);
        }
    }

    @Override
    public Color update(Color color) {
        Objects.requireNonNull(color, "color");
        long expectedVersion = color.version();
        long nextVersion = expectedVersion + 1;
        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE reference.colors
                        SET active = ?, version = ?, updated_at = ?, updated_by = ?
                        WHERE id = ? AND version = ?
                        """,
                        color.active(),
                        nextVersion,
                        Timestamp.from(color.updatedAt()),
                        color.updatedBy(),
                        color.id().value(),
                        expectedVersion);
        if (updated == 0) {
            throw new Color.OptimisticLockConflictException(
                    "Color version conflict: " + color.id());
        }
        return Color.rehydrate(
                color.id(),
                color.code(),
                color.codeKey(),
                color.active(),
                nextVersion,
                color.createdAt(),
                color.updatedAt(),
                color.createdBy(),
                color.updatedBy());
    }

    @Override
    public Optional<Color> findById(ColorId id) {
        Objects.requireNonNull(id, "id");
        try {
            return Optional.ofNullable(
                    jdbcTemplate.queryForObject(
                            "SELECT " + SELECT_COLUMNS + " FROM reference.colors WHERE id = ?",
                            ROW_MAPPER,
                            id.value()));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Color> findByCode(String code) {
        Objects.requireNonNull(code, "code");
        try {
            return Optional.ofNullable(
                    jdbcTemplate.queryForObject(
                            "SELECT " + SELECT_COLUMNS + " FROM reference.colors WHERE code = ?",
                            ROW_MAPPER,
                            code));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Color> findByCodeKey(ColorCodeKey codeKey) {
        Objects.requireNonNull(codeKey, "codeKey");
        try {
            return Optional.ofNullable(
                    jdbcTemplate.queryForObject(
                            "SELECT "
                                    + SELECT_COLUMNS
                                    + " FROM reference.colors WHERE code_key = ?",
                            ROW_MAPPER,
                            codeKey.value()));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<Color> search(Boolean activeFilter, String searchText) {
        String normalizedSearch = searchText == null ? "" : searchText.trim();
        StringBuilder sql =
                new StringBuilder("SELECT ")
                        .append(SELECT_COLUMNS)
                        .append(" FROM reference.colors WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (activeFilter != null) {
            sql.append(" AND active = ?");
            args.add(activeFilter);
        }
        if (!normalizedSearch.isEmpty()) {
            sql.append(" AND code ILIKE ? ESCAPE '\\'");
            args.add("%" + escapeLike(normalizedSearch) + "%");
        }
        sql.append(" ORDER BY code");
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Color mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        return Color.rehydrate(
                ColorId.of(rs.getObject("id", UUID.class)),
                rs.getString("code"),
                ColorCodeKey.of(rs.getString("code")),
                rs.getBoolean("active"),
                rs.getLong("version"),
                createdAt.toInstant(),
                updatedAt.toInstant(),
                rs.getObject("created_by", UUID.class),
                rs.getObject("updated_by", UUID.class));
    }
}
