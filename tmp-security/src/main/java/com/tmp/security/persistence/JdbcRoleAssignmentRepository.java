package com.tmp.security.persistence;

import com.tmp.security.api.RoleId;
import com.tmp.security.api.UserId;
import com.tmp.security.domain.RoleAssignment;
import com.tmp.security.domain.repository.RoleAssignmentRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Constructor stores Spring-managed JdbcTemplate injected by the container.")
public final class JdbcRoleAssignmentRepository implements RoleAssignmentRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcRoleAssignmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void assign(RoleAssignment assignment) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO security.user_roles (user_id, role_id, assigned_at)
                    VALUES (?, ?, ?)
                    """,
                    assignment.userId().value(),
                    assignment.roleId().value(),
                    Timestamp.from(assignment.assignedAt()));
        } catch (DuplicateKeyException ignored) {
            // idempotent
        }
    }

    @Override
    public void revoke(UserId userId, RoleId roleId) {
        jdbcTemplate.update(
                "DELETE FROM security.user_roles WHERE user_id = ? AND role_id = ?",
                userId.value(),
                roleId.value());
    }

    @Override
    public Set<RoleId> findRoleIdsForUser(UserId userId) {
        List<UUID> ids = jdbcTemplate.query(
                "SELECT role_id FROM security.user_roles WHERE user_id = ?",
                (rs, rowNum) -> rs.getObject("role_id", UUID.class),
                userId.value());
        Set<RoleId> result = new HashSet<>();
        for (UUID id : ids) {
            result.add(RoleId.of(id));
        }
        return result;
    }

    @Override
    public List<UserId> findUserIdsForRole(RoleId roleId) {
        return jdbcTemplate.query(
                "SELECT user_id FROM security.user_roles WHERE role_id = ?",
                (rs, rowNum) -> UserId.of(rs.getObject("user_id", UUID.class)),
                roleId.value());
    }

    @Override
    public long countUsersForRole(RoleId roleId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM security.user_roles WHERE role_id = ?",
                Long.class,
                roleId.value());
        return count == null ? 0L : count;
    }
}
