package com.tmp.warehouse.persistence;

import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.repository.WarehouseUserResponsibilityRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JDBC adapter for {@code warehouse.warehouse_user_responsibility}.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate and Clock.")
public final class JdbcWarehouseUserResponsibilityRepository
        implements WarehouseUserResponsibilityRepository {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public JdbcWarehouseUserResponsibilityRepository(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void assign(UUID userId, WarehouseId warehouseId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(warehouseId, "warehouseId");
        Instant now = clock.instant();
        jdbcTemplate.update(
                """
                INSERT INTO warehouse.warehouse_user_responsibility
                    (warehouse_id, user_id, assigned_at)
                VALUES (?, ?, ?)
                ON CONFLICT (warehouse_id, user_id) DO NOTHING
                """,
                warehouseId.value(),
                userId,
                java.sql.Timestamp.from(now));
    }

    @Override
    public void remove(UUID userId, WarehouseId warehouseId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(warehouseId, "warehouseId");
        jdbcTemplate.update(
                """
                DELETE FROM warehouse.warehouse_user_responsibility
                WHERE warehouse_id = ? AND user_id = ?
                """,
                warehouseId.value(),
                userId);
    }

    @Override
    public boolean isResponsible(UUID userId, WarehouseId warehouseId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(warehouseId, "warehouseId");
        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FROM warehouse.warehouse_user_responsibility
                        WHERE warehouse_id = ? AND user_id = ?
                        """,
                        Integer.class,
                        warehouseId.value(),
                        userId);
        return count != null && count > 0;
    }

    @Override
    public List<WarehouseId> listWarehouseIdsForUser(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        return jdbcTemplate
                .queryForList(
                        """
                        SELECT warehouse_id
                        FROM warehouse.warehouse_user_responsibility
                        WHERE user_id = ?
                        ORDER BY warehouse_id
                        """,
                        UUID.class,
                        userId)
                .stream()
                .map(WarehouseId::of)
                .toList();
    }

    @Override
    public List<UUID> listUserIdsForWarehouse(WarehouseId warehouseId) {
        Objects.requireNonNull(warehouseId, "warehouseId");
        return jdbcTemplate.queryForList(
                """
                SELECT user_id
                FROM warehouse.warehouse_user_responsibility
                WHERE warehouse_id = ?
                ORDER BY user_id
                """,
                UUID.class,
                warehouseId.value());
    }
}
