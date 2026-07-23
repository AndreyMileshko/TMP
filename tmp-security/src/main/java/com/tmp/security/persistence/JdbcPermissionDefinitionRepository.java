package com.tmp.security.persistence;

import com.tmp.security.api.PermissionId;
import com.tmp.security.domain.OptimisticLockConflictException;
import com.tmp.security.domain.PermissionDefinition;
import com.tmp.security.domain.repository.PermissionDefinitionRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Constructor stores Spring-managed JdbcTemplate injected by the container.")
public final class JdbcPermissionDefinitionRepository implements PermissionDefinitionRepository {

    private static final RowMapper<PermissionDefinition> ROW_MAPPER =
            JdbcPermissionDefinitionRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcPermissionDefinitionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PermissionDefinition save(PermissionDefinition definition) {
        Optional<PermissionDefinition> existing = findById(definition.permissionId());
        if (existing.isEmpty()) {
            jdbcTemplate.update(
                    """
                    INSERT INTO security.permission_definitions (
                        permission_id, display_name, description, active, registered_at, version)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    definition.permissionId().value(),
                    definition.displayName(),
                    definition.description(),
                    definition.active(),
                    Timestamp.from(definition.registeredAt()),
                    definition.version());
            return definition;
        }
        long expectedVersion = definition.version();
        long nextVersion = expectedVersion + 1;
        int updated = jdbcTemplate.update(
                """
                UPDATE security.permission_definitions
                SET display_name = ?, description = ?, active = ?, version = ?
                WHERE permission_id = ? AND version = ?
                """,
                definition.displayName(),
                definition.description(),
                definition.active(),
                nextVersion,
                definition.permissionId().value(),
                expectedVersion);
        if (updated == 0) {
            throw new OptimisticLockConflictException(
                    "Permission definition version conflict: " + definition.permissionId());
        }
        return PermissionDefinition.rehydrate(
                definition.permissionId(),
                definition.displayName(),
                definition.description(),
                definition.active(),
                definition.registeredAt(),
                nextVersion);
    }

    @Override
    public Optional<PermissionDefinition> findById(PermissionId permissionId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT * FROM security.permission_definitions WHERE permission_id = ?",
                    ROW_MAPPER,
                    permissionId.value()));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<PermissionDefinition> findAll() {
        return jdbcTemplate.query(
                "SELECT * FROM security.permission_definitions ORDER BY permission_id",
                ROW_MAPPER);
    }

    private static PermissionDefinition mapRow(ResultSet rs, int rowNum) throws SQLException {
        return PermissionDefinition.rehydrate(
                PermissionId.of(rs.getString("permission_id")),
                rs.getString("display_name"),
                rs.getString("description"),
                rs.getBoolean("active"),
                rs.getTimestamp("registered_at").toInstant(),
                rs.getLong("version"));
    }
}
