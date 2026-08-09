package com.tmp.warehouse.persistence;

import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseOperationId;
import com.tmp.warehouse.domain.WarehouseOperationStatus;
import com.tmp.warehouse.domain.WarehouseOperationType;
import com.tmp.warehouse.persistence.WarehousePersistenceModels.OptimisticLockException;
import com.tmp.warehouse.persistence.WarehousePersistenceModels.StockPositionRow;
import com.tmp.warehouse.persistence.WarehousePersistenceModels.WarehouseOperationRow;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
 * JDBC access for stock positions and operation records (STAGE6-003 / STAGE6-006).
 *
 * <p>Domain-facing movement persistence is provided by {@link JdbcWarehouseMovementRepository}.
 * Domain-facing operation persistence is provided by {@link JdbcWarehouseOperationRepository}.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate and Clock injected by the container.")
public final class JdbcWarehouseStockRepository {

    private static final RowMapper<StockPositionRow> POSITION_MAPPER =
            JdbcWarehouseStockRepository::mapPosition;
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

    public List<StockPositionRow> findPositionsByMaterial(MaterialReference materialReference) {
        Objects.requireNonNull(materialReference, "materialReference");
        return jdbcTemplate.query(
                """
                SELECT id, warehouse_id, storage_cell_id, material_reference, quantity,
                       stock_state, version, created_at, updated_at
                FROM warehouse.stock_positions
                WHERE material_reference = ?
                ORDER BY warehouse_id, storage_cell_id, stock_state
                """,
                POSITION_MAPPER,
                materialReference.materialCode());
    }

    public Optional<StockPositionRow> findPositionByNaturalKey(
            WarehouseId warehouseId,
            StorageCellId storageCellId,
            MaterialReference materialReference,
            StockState stockState) {
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(storageCellId, "storageCellId");
        Objects.requireNonNull(materialReference, "materialReference");
        Objects.requireNonNull(stockState, "stockState");
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                    SELECT id, warehouse_id, storage_cell_id, material_reference, quantity,
                           stock_state, version, created_at, updated_at
                    FROM warehouse.stock_positions
                    WHERE warehouse_id = ?
                      AND storage_cell_id = ?
                      AND material_reference = ?
                      AND stock_state = ?
                    """,
                    POSITION_MAPPER,
                    warehouseId.value(),
                    storageCellId.value(),
                    materialReference.materialCode(),
                    stockState.name()));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public WarehouseOperationRow insertOperation(WarehouseOperationRow row) {
        Objects.requireNonNull(row, "row");
        jdbcTemplate.update(
                """
                INSERT INTO warehouse.warehouse_operations (
                    id, operation_type, status, warehouse_id, storage_cell_id, material_reference,
                    quantity, stock_state, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                row.id().value(),
                row.operationType().name(),
                row.status().name(),
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

    public WarehouseOperationRow updateOperation(WarehouseOperationRow row, long expectedVersion) {
        Objects.requireNonNull(row, "row");
        Instant now = clock.instant();
        long nextVersion = expectedVersion + 1;
        int updated = jdbcTemplate.update(
                """
                UPDATE warehouse.warehouse_operations
                SET status = ?, version = ?, updated_at = ?
                WHERE id = ? AND version = ?
                """,
                row.status().name(),
                nextVersion,
                Timestamp.from(now),
                row.id().value(),
                expectedVersion);
        if (updated == 0) {
            throw new OptimisticLockException("Warehouse operation version conflict: " + row.id());
        }
        return findOperationById(row.id()).orElseThrow();
    }

    public Optional<WarehouseOperationRow> findOperationById(WarehouseOperationId id) {
        Objects.requireNonNull(id, "id");
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                    SELECT id, operation_type, status, warehouse_id, storage_cell_id,
                           material_reference, quantity, stock_state, version, created_at, updated_at
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

    private static WarehouseOperationRow mapOperation(ResultSet rs, int rowNum) throws SQLException {
        return new WarehouseOperationRow(
                WarehouseOperationId.of(rs.getObject("id", UUID.class)),
                WarehouseOperationType.valueOf(rs.getString("operation_type")),
                WarehouseOperationStatus.valueOf(rs.getString("status")),
                WarehouseId.of(rs.getObject("warehouse_id", UUID.class)),
                StorageCellId.of(rs.getObject("storage_cell_id", UUID.class)),
                MaterialReference.of(rs.getString("material_reference")),
                StockQuantity.of(rs.getBigDecimal("quantity")),
                StockState.valueOf(rs.getString("stock_state")),
                rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
