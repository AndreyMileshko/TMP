package com.tmp.warehouse.persistence;

import com.tmp.warehouse.domain.StockPositionId;
import com.tmp.warehouse.domain.WarehouseMovement;
import com.tmp.warehouse.domain.WarehouseOperationType;
import com.tmp.warehouse.domain.repository.WarehouseMovementRepository;
import com.tmp.warehouse.persistence.WarehousePersistenceModels.WarehouseMovementRow;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * JDBC adapter mapping domain {@link WarehouseMovement} to {@code warehouse.warehouse_movements}.
 *
 * <p>Append-only: no update or delete operations are exposed.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate injected by the container.")
public final class JdbcWarehouseMovementRepository implements WarehouseMovementRepository {

    private static final RowMapper<WarehouseMovementRow> MOVEMENT_MAPPER =
            JdbcWarehouseMovementRepository::mapMovement;

    private final JdbcTemplate jdbcTemplate;

    public JdbcWarehouseMovementRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public WarehouseMovement append(WarehouseMovement movement) {
        Objects.requireNonNull(movement, "movement");
        WarehouseMovementRow row = WarehouseMovementRow.fromDomain(movement);
        jdbcTemplate.update(
                """
                INSERT INTO warehouse.warehouse_movements (
                    id, stock_position_id, operation_type, quantity_delta, created_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                row.id(),
                row.stockPositionId(),
                row.operationType().name(),
                row.quantityDelta(),
                Timestamp.from(row.createdAt()));
        return row.toDomain();
    }

    @Override
    public List<WarehouseMovement> findHistoryByStockPosition(StockPositionId stockPositionId) {
        Objects.requireNonNull(stockPositionId, "stockPositionId");
        return jdbcTemplate.query(
                        """
                        SELECT id, stock_position_id, operation_type, quantity_delta, created_at
                        FROM warehouse.warehouse_movements
                        WHERE stock_position_id = ?
                        ORDER BY created_at, id
                        """,
                        MOVEMENT_MAPPER,
                        stockPositionId.value())
                .stream()
                .map(WarehouseMovementRow::toDomain)
                .toList();
    }

    private static WarehouseMovementRow mapMovement(ResultSet rs, int rowNum) throws SQLException {
        return new WarehouseMovementRow(
                rs.getObject("id", UUID.class),
                rs.getObject("stock_position_id", UUID.class),
                WarehouseOperationType.valueOf(rs.getString("operation_type")),
                rs.getBigDecimal("quantity_delta"),
                rs.getTimestamp("created_at").toInstant());
    }
}
