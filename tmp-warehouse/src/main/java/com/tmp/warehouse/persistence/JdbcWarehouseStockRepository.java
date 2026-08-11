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

    private static final String POSITION_SELECT =
            """
            SELECT sp.id, sp.warehouse_id, sp.storage_cell_id, sp.quantity, sp.stock_state,
                   sp.version, sp.created_at, sp.updated_at,
                   mr.id AS material_id, mr.article, mr.name, mr.color, mr.size,
                   mr.unit_of_measure
            """;

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
                    id, warehouse_id, storage_cell_id, material_reference_id, quantity, stock_state,
                    version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                row.id(),
                row.warehouseId().value(),
                row.storageCellId().value(),
                row.materialReference().id().value(),
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
                    POSITION_SELECT
                            + """
                            FROM warehouse.stock_positions sp
                            JOIN warehouse.material_references mr ON sp.material_reference_id = mr.id
                            WHERE sp.id = ?
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
                POSITION_SELECT
                        + """
                        FROM warehouse.stock_positions sp
                        JOIN warehouse.material_references mr ON sp.material_reference_id = mr.id
                        WHERE sp.warehouse_id = ?
                        ORDER BY mr.article, mr.color, mr.size, mr.unit_of_measure, sp.stock_state
                        """,
                POSITION_MAPPER,
                warehouseId.value());
    }

    public List<StockPositionRow> findPositionsByMaterial(MaterialReference materialReference) {
        Objects.requireNonNull(materialReference, "materialReference");
        return jdbcTemplate.query(
                POSITION_SELECT
                        + """
                        FROM warehouse.stock_positions sp
                        JOIN warehouse.material_references mr ON sp.material_reference_id = mr.id
                        WHERE sp.material_reference_id = ?
                        ORDER BY sp.warehouse_id, sp.storage_cell_id, sp.stock_state
                        """,
                POSITION_MAPPER,
                materialReference.id().value());
    }

    public List<StockPositionRow> findPositionsByArticle(String article) {
        Objects.requireNonNull(article, "article");
        return jdbcTemplate.query(
                POSITION_SELECT
                        + """
                        FROM warehouse.stock_positions sp
                        JOIN warehouse.material_references mr ON sp.material_reference_id = mr.id
                        WHERE mr.article = ?
                        ORDER BY sp.warehouse_id, sp.storage_cell_id, sp.stock_state
                        """,
                POSITION_MAPPER,
                article.trim());
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
                    POSITION_SELECT
                            + """
                            FROM warehouse.stock_positions sp
                            JOIN warehouse.material_references mr ON sp.material_reference_id = mr.id
                            WHERE sp.warehouse_id = ?
                              AND sp.storage_cell_id = ?
                              AND sp.material_reference_id = ?
                              AND sp.stock_state = ?
                            """,
                    POSITION_MAPPER,
                    warehouseId.value(),
                    storageCellId.value(),
                    materialReference.id().value(),
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
                    id, operation_type, status, warehouse_id, storage_cell_id, material_reference_id,
                    quantity, stock_state, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                row.id().value(),
                row.operationType().name(),
                row.status().name(),
                row.warehouseId().value(),
                row.storageCellId().value(),
                row.materialReference().id().value(),
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
                    SELECT wo.id, wo.operation_type, wo.status, wo.warehouse_id, wo.storage_cell_id,
                           wo.quantity, wo.stock_state, wo.version, wo.created_at, wo.updated_at,
                           mr.id AS material_id, mr.article, mr.name, mr.color, mr.size,
                           mr.unit_of_measure
                    FROM warehouse.warehouse_operations wo
                    JOIN warehouse.material_references mr ON wo.material_reference_id = mr.id
                    WHERE wo.id = ?
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
                MaterialReference.rehydrate(
                        com.tmp.warehouse.domain.MaterialReferenceId.of(
                                rs.getObject("material_id", UUID.class)),
                        rs.getString("article"),
                        rs.getString("name"),
                        rs.getString("color"),
                        rs.getString("size"),
                        rs.getString("unit_of_measure")),
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
                MaterialReference.rehydrate(
                        com.tmp.warehouse.domain.MaterialReferenceId.of(
                                rs.getObject("material_id", UUID.class)),
                        rs.getString("article"),
                        rs.getString("name"),
                        rs.getString("color"),
                        rs.getString("size"),
                        rs.getString("unit_of_measure")),
                StockQuantity.of(rs.getBigDecimal("quantity")),
                StockState.valueOf(rs.getString("stock_state")),
                rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
