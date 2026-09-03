package com.tmp.security.persistence;

import com.tmp.security.api.UserId;
import com.tmp.security.api.UserUiPreferenceService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JDBC adapter for {@code security.user_ui_preferences}. Stores opaque per-user UI values.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate and Clock injected by the container.")
public final class JdbcUserUiPreferenceService implements UserUiPreferenceService {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public JdbcUserUiPreferenceService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<String> load(UserId userId, String namespace, String preferenceKey) {
        Objects.requireNonNull(userId, "userId");
        String ns = requireToken(namespace, "namespace");
        String key = requireToken(preferenceKey, "preferenceKey");
        List<String> values =
                jdbcTemplate.query(
                        """
                        SELECT value_text
                          FROM security.user_ui_preferences
                         WHERE user_id = ? AND namespace = ? AND preference_key = ?
                        """,
                        (rs, rowNum) -> rs.getString("value_text"),
                        userId.value(),
                        ns,
                        key);
        if (values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(values.getFirst());
    }

    @Override
    public void save(
            UserId userId, String namespace, String preferenceKey, int preferenceVersion, String value) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(value, "value");
        if (preferenceVersion < 1) {
            throw new IllegalArgumentException("preferenceVersion must be >= 1: " + preferenceVersion);
        }
        String ns = requireToken(namespace, "namespace");
        String key = requireToken(preferenceKey, "preferenceKey");
        Timestamp now = Timestamp.from(clock.instant());
        jdbcTemplate.update(
                """
                INSERT INTO security.user_ui_preferences (
                    user_id, namespace, preference_key, preference_version, value_text, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id, namespace, preference_key)
                DO UPDATE SET preference_version = EXCLUDED.preference_version,
                              value_text = EXCLUDED.value_text,
                              updated_at = EXCLUDED.updated_at
                """,
                userId.value(),
                ns,
                key,
                preferenceVersion,
                value,
                now);
    }

    private static String requireToken(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 128) {
            throw new IllegalArgumentException(field + " must be 1..128 characters");
        }
        return trimmed;
    }
}
