package com.tmp.warehouse.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockPosition;
import com.tmp.warehouse.domain.StockPositionId;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.StorageCell;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseMovement;
import com.tmp.warehouse.domain.WarehouseMovementId;
import com.tmp.warehouse.domain.WarehouseOperationType;
import com.tmp.warehouse.domain.repository.StockPositionRepository;
import com.tmp.warehouse.domain.repository.WarehouseMovementRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
 * Integration tests: append-only Warehouse Movement persistence against warehouse_movements.
 */
@Testcontainers
class JdbcWarehouseMovementRepositoryTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-08-07T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private StockPositionRepository stockPositions;
    private WarehouseMovementRepository movements;

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
        jdbc.update("DELETE FROM warehouse.material_references");

        JdbcWarehouseStockRepository stockJdbc = new JdbcWarehouseStockRepository(jdbc, CLOCK);
        stockPositions = new JdbcStockPositionRepository(stockJdbc);
        movements = new JdbcWarehouseMovementRepository(jdbc);
    }

    @Test
    void appendAndReadMovementHistoryFromDatabase() {
        WarehouseId warehouseId = WarehouseId.generate();
        StorageCellId cellId = StorageCellId.generate();
        new JdbcWarehouseCatalogRepository(jdbc, CLOCK).insert(Warehouse.create(warehouseId, "WH-MV-1", "Movement"));
        new JdbcWarehouseCatalogRepository(jdbc, CLOCK)
                .insert(StorageCell.create(cellId, warehouseId, "A-01"));

        StockPositionId positionId = StockPositionId.generate();
        MaterialReference material =
                new JdbcMaterialReferenceRepository(jdbc, CLOCK)
                        .create(MaterialReference.legacyArticle("ALU-6060"));
        stockPositions.create(
                StockPosition.of(
                        positionId,
                        warehouseId,
                        cellId,
                        material,
                        StockState.AVAILABLE,
                        StockQuantity.of(10L)));

        WarehouseMovementId firstId = WarehouseMovementId.generate();
        WarehouseMovementId secondId = WarehouseMovementId.generate();
        Instant firstAt = FIXED_TIME;
        Instant secondAt = FIXED_TIME.plusSeconds(1);

        movements.append(
                WarehouseMovement.record(
                        firstId,
                        positionId,
                        WarehouseOperationType.RECEIPT,
                        new BigDecimal("5.000000"),
                        firstAt));
        movements.append(
                WarehouseMovement.record(
                        secondId,
                        positionId,
                        WarehouseOperationType.CONSUMPTION,
                        new BigDecimal("-2.000000"),
                        secondAt));

        List<WarehouseMovement> history = movements.findHistoryByStockPosition(positionId);
        assertEquals(2, history.size());
        assertEquals(firstId, history.get(0).id());
        assertEquals(WarehouseOperationType.RECEIPT, history.get(0).operationType());
        assertEquals(0, history.get(0).quantityDelta().compareTo(new BigDecimal("5.000000")));
        assertEquals(firstAt, history.get(0).createdAt());
        assertEquals(positionId, history.get(0).stockPositionId());

        assertEquals(secondId, history.get(1).id());
        assertEquals(WarehouseOperationType.CONSUMPTION, history.get(1).operationType());
        assertEquals(0, history.get(1).quantityDelta().compareTo(new BigDecimal("-2.000000")));
        assertEquals(secondAt, history.get(1).createdAt());
    }

    @Test
    void movementHistoryIsEmptyForUnknownStockPosition() {
        assertTrue(movements.findHistoryByStockPosition(StockPositionId.generate()).isEmpty());
    }
}
