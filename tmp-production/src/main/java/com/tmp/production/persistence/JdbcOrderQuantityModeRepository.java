package com.tmp.production.persistence;

import com.tmp.production.domain.OrderQuantityModeOptimisticLockException;
import com.tmp.production.domain.OrderQuantityModeSetting;
import com.tmp.production.domain.ProductionQuantityMode;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.repository.OrderQuantityModeRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JDBC adapter for Production-owned per-order quantity mode settings
 * ({@code production.order_quantity_modes} only).
 *
 * <p>Each write is a single conditional statement, so concurrent writers with the same expected
 * version cannot both succeed.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate and Clock injected by the container.")
public final class JdbcOrderQuantityModeRepository implements OrderQuantityModeRepository {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public JdbcOrderQuantityModeRepository(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<OrderQuantityModeSetting> findBySourceOrderId(SourceOrderId sourceOrderId) {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        return Optional.ofNullable(
                findBySourceOrderIds(List.of(sourceOrderId)).get(sourceOrderId));
    }

    @Override
    public Map<SourceOrderId, OrderQuantityModeSetting> findBySourceOrderIds(
            Collection<SourceOrderId> sourceOrderIds) {
        Objects.requireNonNull(sourceOrderIds, "sourceOrderIds");
        if (sourceOrderIds.isEmpty()) {
            return Map.of();
        }
        List<SourceOrderId> unique = sourceOrderIds.stream().distinct().toList();
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT source_order_id, quantity_mode, version
                        FROM production.order_quantity_modes
                        WHERE source_order_id IN (
                        """);
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < unique.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
            args.add(unique.get(i).value());
        }
        sql.append(")");
        Map<SourceOrderId, OrderQuantityModeSetting> result = new LinkedHashMap<>();
        jdbcTemplate.query(
                sql.toString(),
                rs -> {
                    SourceOrderId orderId =
                            SourceOrderId.of(rs.getObject("source_order_id", UUID.class));
                    result.put(
                            orderId,
                            new OrderQuantityModeSetting(
                                    orderId,
                                    ProductionQuantityMode.valueOf(rs.getString("quantity_mode")),
                                    rs.getLong("version")));
                },
                args.toArray());
        return Map.copyOf(result);
    }

    @Override
    public OrderQuantityModeSetting save(
            SourceOrderId sourceOrderId, ProductionQuantityMode quantityMode, long expectedVersion) {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        Objects.requireNonNull(quantityMode, "quantityMode");
        if (expectedVersion < 0L) {
            throw new IllegalArgumentException("expectedVersion must be >= 0");
        }
        Timestamp now = Timestamp.from(clock.instant());
        int updated =
                expectedVersion == 0L
                        ? jdbcTemplate.update(
                                """
                                INSERT INTO production.order_quantity_modes (
                                    source_order_id, quantity_mode, version, created_at, updated_at)
                                VALUES (?, ?, 1, ?, ?)
                                ON CONFLICT (source_order_id) DO NOTHING
                                """,
                                sourceOrderId.value(),
                                quantityMode.name(),
                                now,
                                now)
                        : jdbcTemplate.update(
                                """
                                UPDATE production.order_quantity_modes
                                SET quantity_mode = ?,
                                    version = version + 1,
                                    updated_at = ?
                                WHERE source_order_id = ? AND version = ?
                                """,
                                quantityMode.name(),
                                now,
                                sourceOrderId.value(),
                                expectedVersion);
        if (updated == 0) {
            throw new OrderQuantityModeOptimisticLockException(sourceOrderId, expectedVersion);
        }
        return findBySourceOrderId(sourceOrderId).orElseThrow();
    }
}
