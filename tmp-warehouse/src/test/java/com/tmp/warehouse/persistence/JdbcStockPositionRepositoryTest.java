package com.tmp.warehouse.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.tmp.warehouse.domain.repository.StockPositionRepository;
import com.tmp.warehouse.persistence.WarehousePersistenceModels.OptimisticLockException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
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
 * Integration tests: domain StockPosition persistence against warehouse.stock_positions.
 */
@Testcontainers
class JdbcStockPositionRepositoryTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-07T08:00:00Z"), ZoneOffset.UTC);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private JdbcWarehouseCatalogRepository catalog;
    private StockPositionRepository stockPositions;

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
        stockPositions =
                new JdbcStockPositionRepository(new JdbcWarehouseStockRepository(jdbc, CLOCK));
    }

    @Test
    void createAndReadStockPositionFromDatabase() {
        WarehouseId warehouseId = WarehouseId.generate();
        StorageCellId cellId = StorageCellId.generate();
        catalog.insert(Warehouse.create(warehouseId, "WH-SP-1", "Stock Persist"));
        catalog.insert(StorageCell.create(cellId, warehouseId, "A-10"));

        StockPositionId id = StockPositionId.generate();
        MaterialReference material = MaterialReference.of("VEKA-103.211");
        StockPosition created =
                stockPositions.create(
                        StockPosition.of(
                                id,
                                warehouseId,
                                cellId,
                                material,
                                StockState.AVAILABLE,
                                StockQuantity.of(new BigDecimal("12.500000"))));

        assertEquals(id, created.id());
        assertEquals(0L, created.version());
        assertEquals(StockState.AVAILABLE, created.stockState());
        assertEquals(0, created.quantity().value().compareTo(new BigDecimal("12.500000")));
        assertEquals("VEKA-103.211", created.material().materialCode());

        Optional<StockPosition> byId = stockPositions.findById(id);
        assertTrue(byId.isPresent());
        assertEquals(created, byId.get());
        assertEquals(material, byId.get().material());
        assertEquals(StockState.AVAILABLE, byId.get().stockState());

        Optional<StockPosition> byKey =
                stockPositions.findByNaturalKey(warehouseId, cellId, material, StockState.AVAILABLE);
        assertTrue(byKey.isPresent());
        assertEquals(id, byKey.get().id());
    }

    @Test
    void updateQuantityAndStateArePersisted() {
        WarehouseId warehouseId = WarehouseId.generate();
        StorageCellId cellId = StorageCellId.generate();
        catalog.insert(Warehouse.create(warehouseId, "WH-SP-2", "Stock Update"));
        catalog.insert(StorageCell.create(cellId, warehouseId, "B-20"));

        StockPositionId id = StockPositionId.generate();
        stockPositions.create(
                StockPosition.of(
                        id,
                        warehouseId,
                        cellId,
                        MaterialReference.of("STEEL-S235"),
                        StockState.AVAILABLE,
                        StockQuantity.of(10L)));

        StockPosition afterQty =
                stockPositions.updateQuantity(id, StockQuantity.of(15L), 0L);
        assertEquals(StockQuantity.of(15L), afterQty.quantity());
        assertEquals(StockState.AVAILABLE, afterQty.stockState());
        assertEquals(1L, afterQty.version());

        StockPosition afterState =
                stockPositions.updateState(id, StockState.BLOCKED, 1L);
        assertEquals(StockState.BLOCKED, afterState.stockState());
        assertEquals(StockQuantity.of(15L), afterState.quantity());
        assertEquals(2L, afterState.version());

        StockPosition loaded = stockPositions.findById(id).orElseThrow();
        assertEquals(StockState.BLOCKED, loaded.stockState());
        assertEquals(StockQuantity.of(15L), loaded.quantity());
        assertEquals(2L, loaded.version());

        assertTrue(
                stockPositions
                        .findByNaturalKey(
                                warehouseId,
                                cellId,
                                MaterialReference.of("STEEL-S235"),
                                StockState.AVAILABLE)
                        .isEmpty());
        assertTrue(
                stockPositions
                        .findByNaturalKey(
                                warehouseId,
                                cellId,
                                MaterialReference.of("STEEL-S235"),
                                StockState.BLOCKED)
                        .isPresent());
    }

    @Test
    void optimisticLockRejectsStaleQuantityUpdate() {
        WarehouseId warehouseId = WarehouseId.generate();
        StorageCellId cellId = StorageCellId.generate();
        catalog.insert(Warehouse.create(warehouseId, "WH-SP-3", "Stock Lock"));
        catalog.insert(StorageCell.create(cellId, warehouseId, "C-30"));
        StockPositionId id = StockPositionId.generate();
        stockPositions.create(
                StockPosition.of(
                        id,
                        warehouseId,
                        cellId,
                        MaterialReference.of("ALU"),
                        StockState.AVAILABLE,
                        StockQuantity.of(1L)));
        stockPositions.updateQuantity(id, StockQuantity.of(2L), 0L);

        assertThrows(
                OptimisticLockException.class,
                () -> stockPositions.updateQuantity(id, StockQuantity.of(3L), 0L));
    }

    @Test
    void stockStateMappingRoundTripsAllAllowedStates() {
        WarehouseId warehouseId = WarehouseId.generate();
        StorageCellId cellId = StorageCellId.generate();
        catalog.insert(Warehouse.create(warehouseId, "WH-SP-4", "States"));
        catalog.insert(StorageCell.create(cellId, warehouseId, "D-40"));

        for (StockState state : StockState.values()) {
            StockPositionId id = StockPositionId.generate();
            StockPosition saved =
                    stockPositions.create(
                            StockPosition.of(
                                    id,
                                    warehouseId,
                                    cellId,
                                    MaterialReference.of("MAT-" + state.name()),
                                    state,
                                    StockQuantity.zero()));
            assertEquals(state, stockPositions.findById(saved.id()).orElseThrow().stockState());
        }
    }
}
