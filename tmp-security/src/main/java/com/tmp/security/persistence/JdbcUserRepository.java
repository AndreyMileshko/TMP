package com.tmp.security.persistence;

import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.UserId;
import com.tmp.security.domain.DuplicateLoginException;
import com.tmp.security.domain.OptimisticLockConflictException;
import com.tmp.security.domain.PasswordHash;
import com.tmp.security.domain.User;
import com.tmp.security.domain.UserStatus;
import com.tmp.security.domain.repository.UserRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Constructor stores Spring-managed JdbcTemplate injected by the container.")
public final class JdbcUserRepository implements UserRepository {

    private static final RowMapper<User> ROW_MAPPER = JdbcUserRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public User save(User user) {
        Optional<User> existing = findById(user.id());
        try {
            if (existing.isEmpty()) {
                jdbcTemplate.update(
                        """
                        INSERT INTO security.users (
                            id, login, display_name, password_hash, status, version, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        user.id().value(),
                        user.login().value(),
                        user.displayName().value(),
                        user.passwordHash().encodedValue(),
                        user.status().name(),
                        user.version(),
                        Timestamp.from(user.createdAt()),
                        Timestamp.from(user.updatedAt()));
                return user;
            }
            long expectedVersion = user.version();
            long nextVersion = expectedVersion + 1;
            int updated = jdbcTemplate.update(
                    """
                    UPDATE security.users
                    SET login = ?, display_name = ?, password_hash = ?, status = ?,
                        version = ?, updated_at = ?
                    WHERE id = ? AND version = ?
                    """,
                    user.login().value(),
                    user.displayName().value(),
                    user.passwordHash().encodedValue(),
                    user.status().name(),
                    nextVersion,
                    Timestamp.from(user.updatedAt()),
                    user.id().value(),
                    expectedVersion);
            if (updated == 0) {
                throw new OptimisticLockConflictException("User version conflict: " + user.id());
            }
            return User.rehydrate(
                    user.id(),
                    user.login(),
                    user.displayName(),
                    user.passwordHash(),
                    user.status(),
                    nextVersion,
                    user.createdAt(),
                    user.updatedAt());
        } catch (DuplicateKeyException ex) {
            throw new DuplicateLoginException("Login already exists (case-insensitive): " + user.login(), ex);
        }
    }

    @Override
    public Optional<User> findById(UserId id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT * FROM security.users WHERE id = ?",
                    ROW_MAPPER,
                    id.value()));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<User> findByLoginIgnoreCase(Login login) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT * FROM security.users WHERE lower(login) = lower(?)",
                    ROW_MAPPER,
                    login.value()));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public boolean existsByLoginIgnoreCase(Login login) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM security.users WHERE lower(login) = lower(?)",
                Long.class,
                login.value());
        return count != null && count > 0;
    }

    @Override
    public boolean existsAny() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM security.users", Long.class);
        return count != null && count > 0;
    }

    @Override
    public List<User> findPage(int pageIndex, int pageSize, UserStatus statusFilter) {
        if (statusFilter == null) {
            return jdbcTemplate.query(
                    """
                    SELECT * FROM security.users
                    ORDER BY login
                    LIMIT ? OFFSET ?
                    """,
                    ROW_MAPPER,
                    pageSize,
                    pageIndex * pageSize);
        }
        return jdbcTemplate.query(
                """
                SELECT * FROM security.users
                WHERE status = ?
                ORDER BY login
                LIMIT ? OFFSET ?
                """,
                ROW_MAPPER,
                statusFilter.name(),
                pageSize,
                pageIndex * pageSize);
    }

    private static User mapRow(ResultSet rs, int rowNum) throws SQLException {
        return User.rehydrate(
                UserId.of(rs.getObject("id", UUID.class)),
                Login.of(rs.getString("login")),
                DisplayName.of(rs.getString("display_name")),
                PasswordHash.of(rs.getString("password_hash")),
                UserStatus.valueOf(rs.getString("status")),
                rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
