package com.tmp.security.persistence;

import com.tmp.security.api.PermissionId;
import com.tmp.security.api.RoleId;
import com.tmp.security.domain.OptimisticLockConflictException;
import com.tmp.security.domain.Role;
import com.tmp.security.domain.repository.RoleRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Constructor stores Spring-managed JdbcTemplate injected by the container.")
public final class JdbcRoleRepository implements RoleRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcRoleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Role save(Role role) {
        Optional<Role> existing = findById(role.id());
        if (existing.isEmpty()) {
            jdbcTemplate.update(
                    """
                    INSERT INTO security.roles (id, name, description, version, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    role.id().value(),
                    role.name(),
                    role.description(),
                    role.version(),
                    Timestamp.from(role.createdAt()),
                    Timestamp.from(role.updatedAt()));
            replacePermissions(role.id(), role.permissions(), role.updatedAt());
            return role;
        }
        long expectedVersion = role.version();
        long nextVersion = expectedVersion + 1;
        int updated = jdbcTemplate.update(
                """
                UPDATE security.roles
                SET name = ?, description = ?, version = ?, updated_at = ?
                WHERE id = ? AND version = ?
                """,
                role.name(),
                role.description(),
                nextVersion,
                Timestamp.from(role.updatedAt()),
                role.id().value(),
                expectedVersion);
        if (updated == 0) {
            throw new OptimisticLockConflictException("Role version conflict: " + role.id());
        }
        replacePermissions(role.id(), role.permissions(), role.updatedAt());
        return Role.rehydrate(
                role.id(),
                role.name(),
                role.description(),
                role.permissions(),
                nextVersion,
                role.createdAt(),
                role.updatedAt());
    }

    @Override
    public Optional<Role> findById(RoleId id) {
        try {
            RoleHeader header = jdbcTemplate.queryForObject(
                    "SELECT id, name, description, version, created_at, updated_at FROM security.roles WHERE id = ?",
                    (rs, rowNum) -> mapHeader(rs),
                    id.value());
            if (header == null) {
                return Optional.empty();
            }
            return Optional.of(toRole(header, loadPermissions(id)));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<Role> findAll() {
        List<RoleHeader> headers = jdbcTemplate.query(
                "SELECT id, name, description, version, created_at, updated_at FROM security.roles ORDER BY name",
                (rs, rowNum) -> mapHeader(rs));
        return headers.stream()
                .map(header -> toRole(header, loadPermissions(header.id())))
                .toList();
    }

    @Override
    public void deleteById(RoleId id) {
        jdbcTemplate.update("DELETE FROM security.role_permissions WHERE role_id = ?", id.value());
        jdbcTemplate.update("DELETE FROM security.roles WHERE id = ?", id.value());
    }

    private void replacePermissions(RoleId roleId, Set<PermissionId> permissions, Instant grantedAt) {
        jdbcTemplate.update("DELETE FROM security.role_permissions WHERE role_id = ?", roleId.value());
        for (PermissionId permissionId : permissions) {
            jdbcTemplate.update(
                    """
                    INSERT INTO security.role_permissions (role_id, permission_id, granted_at)
                    VALUES (?, ?, ?)
                    """,
                    roleId.value(),
                    permissionId.value(),
                    Timestamp.from(grantedAt));
        }
    }

    private Set<PermissionId> loadPermissions(RoleId roleId) {
        List<String> ids = jdbcTemplate.query(
                "SELECT permission_id FROM security.role_permissions WHERE role_id = ?",
                (rs, rowNum) -> rs.getString("permission_id"),
                roleId.value());
        Set<PermissionId> result = new HashSet<>();
        for (String id : ids) {
            result.add(PermissionId.of(id));
        }
        return result;
    }

    private static RoleHeader mapHeader(ResultSet rs) throws SQLException {
        return new RoleHeader(
                RoleId.of(rs.getObject("id", UUID.class)),
                rs.getString("name"),
                rs.getString("description"),
                rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static Role toRole(RoleHeader header, Set<PermissionId> permissions) {
        return Role.rehydrate(
                header.id(),
                header.name(),
                header.description(),
                permissions,
                header.version(),
                header.createdAt(),
                header.updatedAt());
    }

    private record RoleHeader(
            RoleId id,
            String name,
            String description,
            long version,
            Instant createdAt,
            Instant updatedAt) {
    }
}
