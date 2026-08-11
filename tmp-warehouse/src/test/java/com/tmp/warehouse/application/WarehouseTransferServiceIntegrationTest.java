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
import com.tmp.warehouse.domain.repository.StockPositionRepository;
import com.tmp.warehouse.domain.repository.WarehouseMovementRepository;
import com.tmp.warehouse.domain.repository.WarehouseOperationRepository;
import com.tmp.warehouse.persistence.JdbcStockPositionRepository;
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
 * Integration tests: Transfer send/receive persist operations, movements and stock updates.
 */
@Testcontainers
class WarehouseTransferServiceIntegrationTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-07T17:30:00Z"), ZoneOffset.UTC);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    private JdbcWarehouseCatalogRepository catalog;
    private WarehouseOperationRepository operations;
    private StockPositionRepository stockPositions;
    private WarehouseMovementRepository movements;
    private WarehouseTransferService transfers;

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
        transfers = new WarehouseTransferService(engine);
    }

    @Test
    void transferSendAndReceivePersistOperationsMovementsAndStock() {
        WarehouseId sourceWarehouse = WarehouseId.generate();
        WarehouseId destinationWarehouse = WarehouseId.generate();
        StorageCellId sourceCell = StorageCellId.generate();
        StorageCellId destinationCell = StorageCellId.generate();
        catalog.insert(Warehouse.create(sourceWarehouse, "WH-SRC", "Source"));
        catalog.insert(Warehouse.create(destinationWarehouse, "WH-DST", "Destination"));
        catalog.insert(StorageCell.create(sourceCell, sourceWarehouse, "A-01"));
        catalog.insert(StorageCell.create(destinationCell, destinationWarehouse, "B-01"));

        MaterialReference material = WarehouseJdbcTestSupport.persistLegacyArticle(jdbc, CLOCK, "VEKA 103.211 WHITE");
        stockPositions.create(
                StockPosition.of(
                        sourceWarehouse,
                        sourceCell,
                        material,
                        StockState.AVAILABLE,
                        StockQuantity.of(500L)));

        WarehouseOperation sent =
                transfers.send(
                        new TransferSendRequest(
                                material,
                                StockQuantity.of(500L),
                                sourceWarehouse,
                                sourceCell,
                                destinationWarehouse));

        assertEquals(WarehouseOperationType.TRANSFER_SEND, sent.type());
        assertEquals(WarehouseOperationStatus.COMPLETED, sent.status());
        assertEquals(
                WarehouseOperationStatus.COMPLETED,
                operations.findById(sent.id()).orElseThrow().status());
        assertEquals(
                StockQuantity.zero(),
                stockPositions
                        .findByNaturalKey(
                                sourceWarehouse, sourceCell, material, StockState.AVAILABLE)
                        .orElseThrow()
                        .quantity());
        assertEquals(
                StockQuantity.of(500L),
                stockPositions
                        .findByNaturalKey(
                                sourceWarehouse, sourceCell, material, StockState.IN_TRANSIT)
                        .orElseThrow()
                        .quantity());

        StockPosition inTransit =
                stockPositions
                        .findByNaturalKey(
                                sourceWarehouse, sourceCell, material, StockState.IN_TRANSIT)
                        .orElseThrow();
        List<WarehouseMovement> sendHistory = movements.findHistoryByStockPosition(inTransit.id());
        assertEquals(1, sendHistory.size());
        assertEquals(WarehouseOperationType.TRANSFER_SEND, sendHistory.get(0).operationType());
        assertEquals(0, sendHistory.get(0).quantityDelta().compareTo(BigDecimal.valueOf(500L)));

        WarehouseOperation received =
                transfers.receive(
                        new TransferReceiveRequest(
                                material,
                                StockQuantity.of(500L),
                                sourceWarehouse,
                                sourceCell,
                                destinationWarehouse,
                                destinationCell));

        assertEquals(WarehouseOperationType.TRANSFER_RECEIVE, received.type());
        assertEquals(WarehouseOperationStatus.COMPLETED, received.status());
        assertEquals(
                WarehouseOperationStatus.COMPLETED,
                operations.findById(received.id()).orElseThrow().status());

        assertEquals(
                StockQuantity.zero(),
                stockPositions
                        .findByNaturalKey(
                                sourceWarehouse, sourceCell, material, StockState.IN_TRANSIT)
                        .orElseThrow()
                        .quantity());
        StockPosition destination =
                stockPositions
                        .findByNaturalKey(
                                destinationWarehouse,
                                destinationCell,
                                material,
                                StockState.AVAILABLE)
                        .orElseThrow();
        assertEquals(StockQuantity.of(500L), destination.quantity());

        List<WarehouseMovement> receiveHistory =
                movements.findHistoryByStockPosition(destination.id());
        assertEquals(1, receiveHistory.size());
        assertEquals(
                WarehouseOperationType.TRANSFER_RECEIVE, receiveHistory.get(0).operationType());
        assertTrue(receiveHistory.get(0).quantityDelta().signum() > 0);
        assertEquals(0, receiveHistory.get(0).quantityDelta().compareTo(BigDecimal.valueOf(500L)));
    }
}
