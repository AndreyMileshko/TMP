package com.tmp.security.persistence;

import com.tmp.security.api.PermissionId;
import com.tmp.security.api.UserId;
import com.tmp.security.domain.IndividualPermissionOverride;
import com.tmp.security.domain.OptimisticLockConflictException;
import com.tmp.security.domain.PermissionOverrideDecision;
import com.tmp.security.domain.repository.PermissionOverrideRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Constructor stores Spring-managed JdbcTemplate injected by the container.")
public final class JdbcPermissionOverrideRepository implements PermissionOverrideRepository {

    private static final RowMapper<IndividualPermissionOverride> ROW_MAPPER =
            JdbcPermissionOverrideRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcPermissionOverrideRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public IndividualPermissionOverride save(IndividualPermissionOverride override) {
        Optional<IndividualPermissionOverride> existing =
                findByUserAndPermission(override.userId(), override.permissionId());
        if (existing.isEmpty()) {
            jdbcTemplate.update(
                    """
                    INSERT INTO security.user_permission_overrides (
                        user_id, permission_id, decision, updated_at, version)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    override.userId().value(),
                    override.permissionId().value(),
                    override.decision().name(),
                    Timestamp.from(override.updatedAt()),
                    override.version());
            return override;
        }
        long expectedVersion = override.version();
        long nextVersion = expectedVersion + 1;
        int updated = jdbcTemplate.update(
                """
                UPDATE security.user_permission_overrides
                SET decision = ?, updated_at = ?, version = ?
                WHERE user_id = ? AND permission_id = ? AND version = ?
                """,
                override.decision().name(),
                Timestamp.from(override.updatedAt()),
                nextVersion,
                override.userId().value(),
                override.permissionId().value(),
                expectedVersion);
        if (updated == 0) {
            throw new OptimisticLockConflictException(
                    "Permission override version conflict: "
                            + override.userId() + "/" + override.permissionId());
        }
        return IndividualPermissionOverride.rehydrate(
                override.userId(),
                override.permissionId(),
                override.decision(),
                override.updatedAt(),
                nextVersion);
    }

    @Override
    public void remove(UserId userId, PermissionId permissionId) {
        jdbcTemplate.update(
                "DELETE FROM security.user_permission_overrides WHERE user_id = ? AND permission_id = ?",
                userId.value(),
                permissionId.value());
    }

    @Override
    public List<IndividualPermissionOverride> findByUser(UserId userId) {
        return jdbcTemplate.query(
                "SELECT * FROM security.user_permission_overrides WHERE user_id = ?",
                ROW_MAPPER,
                userId.value());
    }

    @Override
    public Optional<IndividualPermissionOverride> findByUserAndPermission(
            UserId userId, PermissionId permissionId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                    SELECT * FROM security.user_permission_overrides
                    WHERE user_id = ? AND permission_id = ?
                    """,
                    ROW_MAPPER,
                    userId.value(),
                    permissionId.value()));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private static IndividualPermissionOverride mapRow(ResultSet rs, int rowNum) throws SQLException {
        return IndividualPermissionOverride.rehydrate(
                UserId.of(rs.getObject("user_id", UUID.class)),
                PermissionId.of(rs.getString("permission_id")),
                PermissionOverrideDecision.valueOf(rs.getString("decision")),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getLong("version"));
    }
}
