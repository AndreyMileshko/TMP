package com.tmp.security.persistence;

import com.tmp.security.domain.AuditQueryFilter;
import com.tmp.security.domain.AuditOperation;
import com.tmp.security.domain.AuditResult;
import com.tmp.security.domain.SecurityAuditEvent;
import com.tmp.security.domain.repository.SecurityAuditRepository;
import com.tmp.security.api.AuditEventId;
import com.tmp.security.api.UserId;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Constructor stores Spring-managed JdbcTemplate injected by the container.")
public final class JdbcSecurityAuditRepository implements SecurityAuditRepository {

    private static final RowMapper<SecurityAuditEvent> ROW_MAPPER = JdbcSecurityAuditRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcSecurityAuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void append(SecurityAuditEvent event) {
        jdbcTemplate.update(
                """
                INSERT INTO security.security_audit_events (
                    id, occurred_at, actor_user_id, actor_login, operation,
                    target_type, target_id, safe_description, result)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                event.id().value(),
                Timestamp.from(event.occurredAt()),
                event.actorUserId() == null ? null : event.actorUserId().value(),
                event.actorLoginSnapshot(),
                event.operation().name(),
                event.targetType(),
                event.targetIdentifier(),
                event.safeDescription(),
                event.result().name());
    }

    @Override
    public List<SecurityAuditEvent> findPage(AuditQueryFilter filter, int pageIndex, int pageSize) {
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM security.security_audit_events WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        appendFilter(sql, params, filter);
        sql.append(" ORDER BY occurred_at DESC LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add(pageIndex * pageSize);
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    @Override
    public long count(AuditQueryFilter filter) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM security.security_audit_events WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        appendFilter(sql, params, filter);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count == null ? 0L : count;
    }

    private static void appendFilter(StringBuilder sql, List<Object> params, AuditQueryFilter filter) {
        if (filter == null) {
            return;
        }
        if (filter.from() != null) {
            sql.append(" AND occurred_at >= ?");
            params.add(Timestamp.from(filter.from()));
        }
        if (filter.to() != null) {
            sql.append(" AND occurred_at <= ?");
            params.add(Timestamp.from(filter.to()));
        }
        if (filter.actorUserId() != null) {
            sql.append(" AND actor_user_id = ?");
            params.add(filter.actorUserId().value());
        }
        if (filter.operation() != null) {
            sql.append(" AND operation = ?");
            params.add(filter.operation().name());
        }
    }

    private static SecurityAuditEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
        UUID actor = rs.getObject("actor_user_id", UUID.class);
        return SecurityAuditEvent.record(
                AuditEventId.of(rs.getObject("id", UUID.class)),
                rs.getTimestamp("occurred_at").toInstant(),
                actor == null ? null : UserId.of(actor),
                rs.getString("actor_login") == null ? "" : rs.getString("actor_login"),
                AuditOperation.valueOf(rs.getString("operation")),
                rs.getString("target_type"),
                rs.getString("target_id"),
                rs.getString("safe_description"),
                AuditResult.valueOf(rs.getString("result")));
    }
}
