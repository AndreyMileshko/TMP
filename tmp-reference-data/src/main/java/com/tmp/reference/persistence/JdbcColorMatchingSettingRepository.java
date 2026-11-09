package com.tmp.reference.persistence;

import com.tmp.reference.domain.ColorMatchingSettingRepository;
import com.tmp.reference.domain.ColorMatchingSettingRepository.ColorMatchingSetting;
import com.tmp.reference.domain.ColorMatchingSettingRepository.OptimisticLockConflictException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * JDBC adapter for {@code reference.color_matching_settings} (TMP-SPEC-015 section 16.3 / COL-022A).
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate injected by the container.")
public final class JdbcColorMatchingSettingRepository implements ColorMatchingSettingRepository {

    private static final RowMapper<ColorMatchingSetting> ROW_MAPPER =
            JdbcColorMatchingSettingRepository::mapRow;

    private static final String SELECT_COLUMNS =
            """
            automatic_matching_enabled, version, created_at, updated_at, created_by, updated_by
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcColorMatchingSettingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public ColorMatchingSetting get() {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT "
                            + SELECT_COLUMNS
                            + " FROM reference.color_matching_settings WHERE id = ?",
                    ROW_MAPPER,
                    ColorMatchingSetting.SINGLETON_ID);
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalStateException(
                    "Color matching setting row (id=1) is missing; V59 seed required", ex);
        }
    }

    @Override
    public ColorMatchingSetting update(ColorMatchingSetting setting) {
        Objects.requireNonNull(setting, "setting");
        long expectedVersion = setting.version();
        long nextVersion = expectedVersion + 1;
        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE reference.color_matching_settings
                        SET automatic_matching_enabled = ?, version = ?, updated_at = ?, updated_by = ?
                        WHERE id = ? AND version = ?
                        """,
                        setting.automaticMatchingEnabled(),
                        nextVersion,
                        Timestamp.from(setting.updatedAt()),
                        setting.updatedBy(),
                        ColorMatchingSetting.SINGLETON_ID,
                        expectedVersion);
        if (updated == 0) {
            throw new OptimisticLockConflictException(
                    "Color matching setting version conflict: expected " + expectedVersion);
        }
        return ColorMatchingSetting.rehydrate(
                setting.automaticMatchingEnabled(),
                nextVersion,
                setting.createdAt(),
                setting.updatedAt(),
                setting.createdBy(),
                setting.updatedBy());
    }

    private static ColorMatchingSetting mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        return ColorMatchingSetting.rehydrate(
                rs.getBoolean("automatic_matching_enabled"),
                rs.getLong("version"),
                createdAt.toInstant(),
                updatedAt.toInstant(),
                rs.getObject("created_by", java.util.UUID.class),
                rs.getObject("updated_by", java.util.UUID.class));
    }
}