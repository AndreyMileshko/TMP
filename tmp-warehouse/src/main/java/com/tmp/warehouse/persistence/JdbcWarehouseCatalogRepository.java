package com.tmp.warehouse.persistence;

import com.tmp.warehouse.domain.StorageCell;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.repository.WarehouseCatalogRepository;
import com.tmp.warehouse.persistence.WarehousePersistenceModels.OptimisticLockException;
import com.tmp.warehouse.persistence.WarehousePersistenceModels.StorageCellRow;
import com.tmp.warehouse.persistence.WarehousePersistenceModels.WarehouseRow;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * JDBC access for {@code warehouse.warehouses} and {@code warehouse.storage_cells}.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate and Clock injected by the container.")
public final class JdbcWarehouseCatalogRepository implements WarehouseCatalogRepository {

    private static final RowMapper<WarehouseRow> WAREHOUSE_MAPPER =
            JdbcWarehouseCatalogRepository::mapWarehouse;
    private static final RowMapper<StorageCellRow> CELL_MAPPER =
            JdbcWarehouseCatalogRepository::mapCell;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public JdbcWarehouseCatalogRepository(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public List<Warehouse> findAll() {
        return jdbcTemplate
                .query(
                        """
                        SELECT id, code, name, active, version, created_at, updated_at
                        FROM warehouse.warehouses
                        ORDER BY code
                        """,
                        WAREHOUSE_MAPPER)
                .stream()
                .map(WarehouseRow::toDomain)
                .toList();
    }

    @Override
    public Warehouse save(Warehouse warehouse) {
        return insert(warehouse).toDomain();
    }

    @Override
    public StorageCell save(StorageCell cell) {
        return insert(cell).toDomain();
    }

    @Override
    public List<StorageCell> findStorageCellsByWarehouse(WarehouseId warehouseId) {
        return findStorageCellsByWarehouseRows(warehouseId).stream()
                .map(StorageCellRow::toDomain)
                .toList();
    }

    public WarehouseRow insert(Warehouse warehouse) {
        Objects.requireNonNull(warehouse, "warehouse");
        Instant now = clock.instant();
        WarehouseRow row = WarehouseRow.fromDomain(warehouse, 0L, now, now);
        jdbcTemplate.update(
                """
                INSERT INTO warehouse.warehouses (
                    id, code, name, active, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                row.id().value(),
                row.code(),
                row.name(),
                row.active(),
                row.version(),
                Timestamp.from(row.createdAt()),
                Timestamp.from(row.updatedAt()));
        return row;
    }

    public WarehouseRow update(Warehouse warehouse, long expectedVersion) {
        Objects.requireNonNull(warehouse, "warehouse");
        Instant now = clock.instant();
        long nextVersion = expectedVersion + 1;
        int updated = jdbcTemplate.update(
                """
                UPDATE warehouse.warehouses
                SET code = ?, name = ?, active = ?, version = ?, updated_at = ?
                WHERE id = ? AND version = ?
                """,
                warehouse.code(),
                warehouse.name(),
                warehouse.active(),
                nextVersion,
                Timestamp.from(now),
                warehouse.id().value(),
                expectedVersion);
        if (updated == 0) {
            throw new OptimisticLockException("Warehouse version conflict: " + warehouse.id());
        }
        WarehouseRow existing = findWarehouseById(warehouse.id()).orElseThrow();
        return new WarehouseRow(
                warehouse.id(),
                warehouse.code(),
                warehouse.name(),
                warehouse.active(),
                nextVersion,
                existing.createdAt(),
                now);
    }

    public Optional<WarehouseRow> findWarehouseById(WarehouseId id) {
        Objects.requireNonNull(id, "id");
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                    SELECT id, code, name, active, version, created_at, updated_at
                    FROM warehouse.warehouses
                    WHERE id = ?
                    """,
                    WAREHOUSE_MAPPER,
                    id.value()));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<WarehouseRow> findWarehouseByCode(String code) {
        Objects.requireNonNull(code, "code");
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                    SELECT id, code, name, active, version, created_at, updated_at
                    FROM warehouse.warehouses
                    WHERE code = ?
                    """,
                    WAREHOUSE_MAPPER,
                    code));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public StorageCellRow insert(StorageCell cell) {
        Objects.requireNonNull(cell, "cell");
        Instant now = clock.instant();
        StorageCellRow row = StorageCellRow.fromDomain(cell, 0L, now, now);
        jdbcTemplate.update(
                """
                INSERT INTO warehouse.storage_cells (
                    id, warehouse_id, code, active, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                row.id().value(),
                row.warehouseId().value(),
                row.code(),
                row.active(),
                row.version(),
                Timestamp.from(row.createdAt()),
                Timestamp.from(row.updatedAt()));
        return row;
    }

    public StorageCellRow update(StorageCell cell, long expectedVersion) {
        Objects.requireNonNull(cell, "cell");
        Instant now = clock.instant();
        long nextVersion = expectedVersion + 1;
        int updated = jdbcTemplate.update(
                """
                UPDATE warehouse.storage_cells
                SET code = ?, active = ?, version = ?, updated_at = ?
                WHERE id = ? AND version = ?
                """,
                cell.code(),
                cell.active(),
                nextVersion,
                Timestamp.from(now),
                cell.id().value(),
                expectedVersion);
        if (updated == 0) {
            throw new OptimisticLockException("Storage cell version conflict: " + cell.id());
        }
        StorageCellRow existing = findStorageCellById(cell.id()).orElseThrow();
        return new StorageCellRow(
                cell.id(),
                cell.warehouseId(),
                cell.code(),
                cell.active(),
                nextVersion,
                existing.createdAt(),
                now);
    }

    public Optional<StorageCellRow> findStorageCellById(StorageCellId id) {
        Objects.requireNonNull(id, "id");
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                    SELECT id, warehouse_id, code, active, version, created_at, updated_at
                    FROM warehouse.storage_cells
                    WHERE id = ?
                    """,
                    CELL_MAPPER,
                    id.value()));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public List<StorageCellRow> findStorageCellsByWarehouseRows(WarehouseId warehouseId) {
        Objects.requireNonNull(warehouseId, "warehouseId");
        return jdbcTemplate.query(
                """
                SELECT id, warehouse_id, code, active, version, created_at, updated_at
                FROM warehouse.storage_cells
                WHERE warehouse_id = ?
                ORDER BY code
                """,
                CELL_MAPPER,
                warehouseId.value());
    }

    private static WarehouseRow mapWarehouse(ResultSet rs, int rowNum) throws SQLException {
        return new WarehouseRow(
                WarehouseId.of(rs.getObject("id", java.util.UUID.class)),
                rs.getString("code"),
                rs.getString("name"),
                rs.getBoolean("active"),
                rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static StorageCellRow mapCell(ResultSet rs, int rowNum) throws SQLException {
        return new StorageCellRow(
                StorageCellId.of(rs.getObject("id", java.util.UUID.class)),
                WarehouseId.of(rs.getObject("warehouse_id", java.util.UUID.class)),
                rs.getString("code"),
                rs.getBoolean("active"),
                rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
