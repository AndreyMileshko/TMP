package com.tmp.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.testsupport.WarehouseJdbcTestSupport;
import com.tmp.warehouse.domain.StockPosition;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.StorageCell;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseMovement;
import com.tmp.warehouse.domain.WarehouseOperation;
import com.tmp.warehouse.domain.WarehouseOperationStatus;
import com.tmp.warehouse.domain.WarehouseOperationType;
import com.tmp.warehouse.domain.repository.MaterialReferenceRepository;
import com.tmp.warehouse.domain.repository.StockPositionRepository;
import com.tmp.warehouse.persistence.JdbcStockPositionRepository;
import com.tmp.warehouse.domain.repository.WarehouseMovementRepository;
import com.tmp.warehouse.domain.repository.WarehouseOperationRepository;
import com.tmp.warehouse.persistence.JdbcMaterialReferenceRepository;
import com.tmp.warehouse.persistence.JdbcWarehouseCatalogRepository;
import com.tmp.warehouse.persistence.JdbcWarehouseMovementRepository;
import com.tmp.warehouse.persistence.JdbcWarehouseOperationRepository;
import com.tmp.warehouse.persistence.JdbcWarehouseStockRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests: Receipt persists Operation, Movement and updated Stock Position.
 */
@Testcontainers
class WarehouseReceiptServiceIntegrationTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-07T15:30:00Z"), ZoneOffset.UTC);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    private JdbcWarehouseCatalogRepository catalog;
    private WarehouseOperationRepository operations;
    private MaterialReferenceRepository materials;
    private StockPositionRepository stockPositions;
    private WarehouseMovementRepository movements;
    private WarehouseReceiptService receipts;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUsername(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        ds.setDriverClassName("org.postgresql.Driver");
        dataSource = ds;
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
        materials = new JdbcMaterialReferenceRepository(jdbc, CLOCK);
        catalog = new JdbcWarehouseCatalogRepository(jdbc, CLOCK);
        operations = new JdbcWarehouseOperationRepository(stockJdbc, CLOCK);
        stockPositions = new JdbcStockPositionRepository(stockJdbc);
        movements = new JdbcWarehouseMovementRepository(jdbc);
        WarehouseOperationEngine engine =
                new WarehouseOperationEngine(
                        operations,
                        stockPositions,
                        movements,
                        new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                        CLOCK);
        receipts = new WarehouseReceiptService(engine, stockPositions, materials);
    }

    @Test
    void receiptPersistsOperationMovementAndStock() {
        WarehouseId warehouseId = WarehouseId.generate();
        StorageCellId cellId = StorageCellId.generate();
        catalog.insert(Warehouse.create(warehouseId, "WH-RCV-1", "Receipt"));
        catalog.insert(StorageCell.create(cellId, warehouseId, "R-01"));

        WarehouseOperation completed =
                receipts.receive(
                        new ReceiptRequest(
                                "ALU-6060",
                                "ALU-6060",
                                "",
                                "",
                                "",
                                StockQuantity.of(new BigDecimal("12.500000")),
                                warehouseId,
                                cellId));

        MaterialReference material = materials.findAll().get(0);

        assertEquals(WarehouseOperationType.RECEIPT, completed.type());
        assertEquals(WarehouseOperationStatus.COMPLETED, completed.status());
        assertEquals(
                WarehouseOperationStatus.COMPLETED,
                operations.findById(completed.id()).orElseThrow().status());

        StockPosition position =
                stockPositions
                        .findByNaturalKey(warehouseId, cellId, material, StockState.AVAILABLE)
                        .orElseThrow();
        assertEquals(0, position.quantity().value().compareTo(new BigDecimal("12.500000")));

        List<WarehouseMovement> history = movements.findHistoryByStockPosition(position.id());
        assertEquals(1, history.size());
        assertEquals(WarehouseOperationType.RECEIPT, history.get(0).operationType());
        assertTrue(history.get(0).quantityDelta().signum() > 0);
        assertEquals(0, history.get(0).quantityDelta().compareTo(new BigDecimal("12.500000")));
    }

    @Test
    void receiptAddsToExistingStockBalance() {
        WarehouseId warehouseId = WarehouseId.generate();
        StorageCellId cellId = StorageCellId.generate();
        catalog.insert(Warehouse.create(warehouseId, "WH-RCV-2", "Receipt Add"));
        catalog.insert(StorageCell.create(cellId, warehouseId, "R-02"));

        MaterialReference material =
                materials.create(MaterialReference.legacyArticle("VEKA-103.211"));
        stockPositions.create(
                StockPosition.of(
                        warehouseId,
                        cellId,
                        material,
                        StockState.AVAILABLE,
                        StockQuantity.of(20L)));

        receipts.receive(
                new ReceiptRequest(
                        "VEKA-103.211",
                        "VEKA-103.211",
                        "",
                        "",
                        "",
                        StockQuantity.of(5L),
                        warehouseId,
                        cellId));

        StockPosition position =
                stockPositions
                        .findByNaturalKey(warehouseId, cellId, material, StockState.AVAILABLE)
                        .orElseThrow();
        assertEquals(StockQuantity.of(25L), position.quantity());
        assertEquals(
                0,
                movements
                        .findHistoryByStockPosition(position.id())
                        .get(0)
                        .quantityDelta()
                        .compareTo(BigDecimal.valueOf(5L)));
    }
}
