package com.tmp.warehouse.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.StorageCell;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseOperationId;
import com.tmp.warehouse.domain.WarehouseOperationType;
import com.tmp.warehouse.persistence.WarehousePersistenceModels.OptimisticLockException;
import com.tmp.warehouse.persistence.WarehousePersistenceModels.StockPositionRow;
import com.tmp.warehouse.persistence.WarehousePersistenceModels.WarehouseMovementRow;
import com.tmp.warehouse.persistence.WarehousePersistenceModels.WarehouseOperationRow;
import com.tmp.warehouse.persistence.WarehousePersistenceModels.WarehouseOperationStatus;
import com.tmp.warehouse.persistence.WarehousePersistenceModels.WarehouseRow;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Persistence integration tests for Warehouse JDBC repositories against PostgreSQL.
 */
@Testcontainers
class JdbcWarehousePersistenceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-07T06:00:00Z"), ZoneOffset.UTC);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private JdbcWarehouseCatalogRepository catalog;
    private JdbcWarehouseStockRepository stock;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM warehouse.warehouse_movements");
        jdbc.update("DELETE FROM warehouse.warehouse_operations");
        jdbc.update("DELETE FROM warehouse.stock_positions");
        jdbc.update("DELETE FROM warehouse.storage_cells");
        jdbc.update("DELETE FROM warehouse.warehouses");
        catalog = new JdbcWarehouseCatalogRepository(jdbc, CLOCK);
        stock = new JdbcWarehouseStockRepository(jdbc, CLOCK);
    }

    @Test
    void warehouseAndCellRoundTrip() {
        WarehouseId warehouseId = WarehouseId.generate();
        Warehouse warehouse = Warehouse.create(warehouseId, "WH-1", "Main");
        WarehouseRow saved = catalog.insert(warehouse);
        assertEquals(warehouseId, saved.id());
        assertEquals(0L, saved.version());
        assertEquals(CLOCK.instant(), saved.createdAt());

        StorageCellId cellId = StorageCellId.generate();
        catalog.insert(StorageCell.create(cellId, warehouseId, "A-01"));

        assertEquals("WH-1", catalog.findWarehouseByCode("WH-1").orElseThrow().code());
        assertEquals(1, catalog.findStorageCellsByWarehouse(warehouseId).size());
        assertTrue(catalog.findStorageCellById(cellId).orElseThrow().toDomain().belongsTo(warehouseId));
    }

    @Test
    void stockPositionMovementAndOperationRoundTrip() {
        WarehouseId warehouseId = WarehouseId.generate();
        StorageCellId cellId = StorageCellId.generate();
        catalog.insert(Warehouse.create(warehouseId, "WH-2", "Secondary"));
        catalog.insert(StorageCell.create(cellId, warehouseId, "B-01"));

        UUID positionId = UUID.randomUUID();
        StockPositionRow position = stock.insertPosition(
                positionId,
                warehouseId,
                cellId,
                MaterialReference.of("ALU-6060"),
                StockQuantity.of(new BigDecimal("12.500000")),
                StockState.AVAILABLE);

        assertEquals(StockState.AVAILABLE, position.stockState());
        assertEquals(0, position.quantity().value().compareTo(new BigDecimal("12.500000")));

        StockPositionRow updated = stock.updatePosition(
                positionId, StockQuantity.of(new BigDecimal("15.000000")), StockState.AVAILABLE, 0L);
        assertEquals(1L, updated.version());

        WarehouseMovementRow movement = stock.insertMovement(
                UUID.randomUUID(),
                positionId,
                WarehouseOperationType.RECEIPT,
                new BigDecimal("2.500000"));
        List<WarehouseMovementRow> history = stock.findMovementsByStockPosition(positionId);
        assertEquals(1, history.size());
        assertEquals(0, history.get(0).quantityDelta().compareTo(movement.quantityDelta()));

        WarehouseOperationId operationId = WarehouseOperationId.generate();
        WarehouseOperationRow operation = stock.insertOperation(
                operationId, WarehouseOperationType.RECEIPT, WarehouseOperationStatus.CREATED);
        assertEquals(WarehouseOperationStatus.CREATED, operation.status());
        assertEquals(
                operationId,
                stock.findOperationById(operationId).orElseThrow().id());
    }

    @Test
    void optimisticLockRejectsStaleStockUpdate() {
        WarehouseId warehouseId = WarehouseId.generate();
        StorageCellId cellId = StorageCellId.generate();
        catalog.insert(Warehouse.create(warehouseId, "WH-3", "Lock"));
        catalog.insert(StorageCell.create(cellId, warehouseId, "C-01"));
        UUID positionId = UUID.randomUUID();
        stock.insertPosition(
                positionId,
                warehouseId,
                cellId,
                MaterialReference.of("STEEL"),
                StockQuantity.of(1L),
                StockState.AVAILABLE);
        stock.updatePosition(positionId, StockQuantity.of(2L), StockState.AVAILABLE, 0L);

        assertThrows(
                OptimisticLockException.class,
                () -> stock.updatePosition(positionId, StockQuantity.of(3L), StockState.BLOCKED, 0L));
    }
}
