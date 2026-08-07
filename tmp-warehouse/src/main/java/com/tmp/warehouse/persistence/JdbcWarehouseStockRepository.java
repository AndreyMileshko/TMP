package com.tmp.warehouse.persistence;

import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseOperationId;
import com.tmp.warehouse.domain.WarehouseOperationType;
import com.tmp.warehouse.persistence.WarehousePersistenceModels.OptimisticLockException;
import com.tmp.warehouse.persistence.WarehousePersistenceModels.StockPositionRow;
import com.tmp.warehouse.persistence.WarehousePersistenceModels.WarehouseMovementRow;
import com.tmp.warehouse.persistence.WarehousePersistenceModels.WarehouseOperationRow;
import com.tmp.warehouse.persistence.WarehousePersistenceModels.WarehouseOperationStatus;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * JDBC access for stock positions, immutable movements and operation records.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate and Clock injected by the container.")
public final class JdbcWarehouseStockRepository {

    private static final RowMapper<StockPositionRow> POSITION_MAPPER =
            JdbcWarehouseStockRepository::mapPosition;
    private static final RowMapper<WarehouseMovementRow> MOVEMENT_MAPPER =
            JdbcWarehouseStockRepository::mapMovement;
    private static final RowMapper<WarehouseOperationRow> OPERATION_MAPPER =
            JdbcWarehouseStockRepository::mapOperation;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public JdbcWarehouseStockRepository(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public StockPositionRow insertPosition(StockPositionRow row) {
        Objects.requireNonNull(row, "row");
        jdbcTemplate.update(
                """
                INSERT INTO warehouse.stock_positions (
                    id, warehouse_id, storage_cell_id, material_reference, quantity, stock_state,
                    version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                row.id(),
                row.warehouseId().value(),
                row.storageCellId().value(),
                row.materialReference().materialCode(),
                row.quantity().value(),
                row.stockState().name(),
                row.version(),
                Timestamp.from(row.createdAt()),
                Timestamp.from(row.updatedAt()));
        return row;
    }

    public StockPositionRow insertPosition(
            UUID id,
            WarehouseId warehouseId,
            StorageCellId storageCellId,
            MaterialReference materialReference,
            StockQuantity quantity,
            StockState stockState) {
        Instant now = clock.instant();
        return insertPosition(new StockPositionRow(
                id, warehouseId, storageCellId, materialReference, quantity, stockState, 0L, now, now));
    }

    public StockPositionRow updatePosition(
            UUID id,
            StockQuantity quantity,
            StockState stockState,
            long expectedVersion) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(stockState, "stockState");
        Instant now = clock.instant();
        long nextVersion = expectedVersion + 1;
        int updated = jdbcTemplate.update(
                """
                UPDATE warehouse.stock_positions
                SET quantity = ?, stock_state = ?, version = ?, updated_at = ?
                WHERE id = ? AND version = ?
                """,
                quantity.value(),
                stockState.name(),
                nextVersion,
                Timestamp.from(now),
                id,
                expectedVersion);
        if (updated == 0) {
            throw new OptimisticLockException("Stock position version conflict: " + id);
        }
        return findPositionById(id).orElseThrow();
    }

    public Optional<StockPositionRow> findPositionById(UUID id) {
        Objects.requireNonNull(id, "id");
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                    SELECT id, warehouse_id, storage_cell_id, material_reference, quantity,
                           stock_state, version, created_at, updated_at
                    FROM warehouse.stock_positions
                    WHERE id = ?
                    """,
                    POSITION_MAPPER,
                    id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public List<StockPositionRow> findPositionsByWarehouse(WarehouseId warehouseId) {
        Objects.requireNonNull(warehouseId, "warehouseId");
        return jdbcTemplate.query(
                """
                SELECT id, warehouse_id, storage_cell_id, material_reference, quantity,
                       stock_state, version, created_at, updated_at
                FROM warehouse.stock_positions
                WHERE warehouse_id = ?
                ORDER BY material_reference, stock_state
                """,
                POSITION_MAPPER,
                warehouseId.value());
    }

    public WarehouseMovementRow insertMovement(WarehouseMovementRow row) {
        Objects.requireNonNull(row, "row");
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
        return row;
    }

    public WarehouseMovementRow insertMovement(
            UUID id,
            UUID stockPositionId,
            WarehouseOperationType operationType,
            BigDecimal quantityDelta) {
        return insertMovement(new WarehouseMovementRow(
                id, stockPositionId, operationType, quantityDelta, clock.instant()));
    }

    public List<WarehouseMovementRow> findMovementsByStockPosition(UUID stockPositionId) {
        Objects.requireNonNull(stockPositionId, "stockPositionId");
        return jdbcTemplate.query(
                """
                SELECT id, stock_position_id, operation_type, quantity_delta, created_at
                FROM warehouse.warehouse_movements
                WHERE stock_position_id = ?
                ORDER BY created_at, id
                """,
                MOVEMENT_MAPPER,
                stockPositionId);
    }

    public WarehouseOperationRow insertOperation(WarehouseOperationRow row) {
        Objects.requireNonNull(row, "row");
        jdbcTemplate.update(
                """
                INSERT INTO warehouse.warehouse_operations (
                    id, operation_type, status, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                row.id().value(),
                row.operationType().name(),
                row.status().name(),
                row.version(),
                Timestamp.from(row.createdAt()),
                Timestamp.from(row.updatedAt()));
        return row;
    }

    public WarehouseOperationRow insertOperation(
            WarehouseOperationId id,
            WarehouseOperationType operationType,
            WarehouseOperationStatus status) {
        Instant now = clock.instant();
        return insertOperation(new WarehouseOperationRow(id, operationType, status, 0L, now, now));
    }

    public Optional<WarehouseOperationRow> findOperationById(WarehouseOperationId id) {
        Objects.requireNonNull(id, "id");
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                    SELECT id, operation_type, status, version, created_at, updated_at
                    FROM warehouse.warehouse_operations
                    WHERE id = ?
                    """,
                    OPERATION_MAPPER,
                    id.value()));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private static StockPositionRow mapPosition(ResultSet rs, int rowNum) throws SQLException {
        return new StockPositionRow(
                rs.getObject("id", UUID.class),
                WarehouseId.of(rs.getObject("warehouse_id", UUID.class)),
                StorageCellId.of(rs.getObject("storage_cell_id", UUID.class)),
                MaterialReference.of(rs.getString("material_reference")),
                StockQuantity.of(rs.getBigDecimal("quantity")),
                StockState.valueOf(rs.getString("stock_state")),
                rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static WarehouseMovementRow mapMovement(ResultSet rs, int rowNum) throws SQLException {
        return new WarehouseMovementRow(
                rs.getObject("id", UUID.class),
                rs.getObject("stock_position_id", UUID.class),
                WarehouseOperationType.valueOf(rs.getString("operation_type")),
                rs.getBigDecimal("quantity_delta"),
                rs.getTimestamp("created_at").toInstant());
    }

    private static WarehouseOperationRow mapOperation(ResultSet rs, int rowNum) throws SQLException {
        return new WarehouseOperationRow(
                WarehouseOperationId.of(rs.getObject("id", UUID.class)),
                WarehouseOperationType.valueOf(rs.getString("operation_type")),
                WarehouseOperationStatus.valueOf(rs.getString("status")),
                rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
