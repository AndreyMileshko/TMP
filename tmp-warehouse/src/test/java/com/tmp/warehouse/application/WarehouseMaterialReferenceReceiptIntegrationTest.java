package com.tmp.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockPosition;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.StorageCell;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseOperation;
import com.tmp.warehouse.domain.repository.MaterialReferenceRepository;
import com.tmp.warehouse.domain.repository.StockPositionRepository;
import com.tmp.warehouse.domain.repository.WarehouseMovementRepository;
import com.tmp.warehouse.domain.repository.WarehouseOperationRepository;
import com.tmp.warehouse.persistence.JdbcMaterialReferenceRepository;
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

/** STAGE6-019: MaterialReference creation and reuse through Receipt; other ops do not create materials. */
@Testcontainers
class WarehouseMaterialReferenceReceiptIntegrationTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-10T12:00:00Z"), ZoneOffset.UTC);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    private MaterialReferenceRepository materials;
    private StockPositionRepository stockPositions;
    private WarehouseReceiptService receipts;
    private WarehouseMoveService moves;
    private WarehouseTransferService transfers;
    private WarehouseConsumptionService consumptions;

    private WarehouseId warehouseId;
    private StorageCellId cellA;
    private StorageCellId cellB;

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
        jdbc.update("DELETE FROM warehouse.transfer_operation_context");
        jdbc.update("DELETE FROM warehouse.warehouse_movements");
        jdbc.update("DELETE FROM warehouse.warehouse_operations");
        jdbc.update("DELETE FROM warehouse.material_reservation_links");
        jdbc.update("DELETE FROM warehouse.stock_positions");
        jdbc.update("DELETE FROM warehouse.storage_cells");
        jdbc.update("DELETE FROM warehouse.warehouses");
        jdbc.update("DELETE FROM warehouse.material_references");

        JdbcWarehouseStockRepository stockJdbc = new JdbcWarehouseStockRepository(jdbc, CLOCK);
        materials = new JdbcMaterialReferenceRepository(jdbc, CLOCK);
        WarehouseOperationRepository operations =
                new JdbcWarehouseOperationRepository(stockJdbc, CLOCK);
        stockPositions = new JdbcStockPositionRepository(stockJdbc);
        WarehouseMovementRepository movements = new JdbcWarehouseMovementRepository(jdbc);
        WarehouseOperationEngine engine =
                new WarehouseOperationEngine(
                        operations,
                        stockPositions,
                        movements,
                        new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                        CLOCK);
        receipts = new WarehouseReceiptService(engine, stockPositions, materials);
        moves = new WarehouseMoveService(engine);
        transfers = new WarehouseTransferService(
                engine,
                operations,
                new com.tmp.warehouse.persistence.JdbcTransferOperationContextRepository(jdbc),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
        consumptions = new WarehouseConsumptionService(engine, stockPositions);

        JdbcWarehouseCatalogRepository catalog = new JdbcWarehouseCatalogRepository(jdbc, CLOCK);
        warehouseId = WarehouseId.generate();
        catalog.save(Warehouse.of(warehouseId, "WH-1", "Main", true));
        cellA = StorageCellId.generate();
        cellB = StorageCellId.generate();
        catalog.save(StorageCell.of(cellA, warehouseId, "A-01", true));
        catalog.save(StorageCell.of(cellB, warehouseId, "B-01", true));
    }

    @Test
    void receiptCreatesNewMaterialReference() {
        receipts.receive(receipt("VEKA-103.211", "Профиль VEKA Softline", "Белый", "6000", "шт.", 100));

        List<MaterialReference> all = materials.findAll();
        assertEquals(1, all.size());
        MaterialReference created = all.get(0);
        assertEquals("VEKA-103.211", created.article());
        assertEquals("Профиль VEKA Softline", created.name());
        assertEquals("Белый", created.color());
        assertEquals("6000", created.size());
        assertEquals("шт.", created.unitOfMeasure());
    }

    @Test
    void repeatedReceiptSameNaturalKeyIncreasesStockWithoutNewMaterial() {
        receipts.receive(receipt("TEST-PROFILE-001", "Тестовый профиль", "Белый", "6000", "шт.", 100));
        receipts.receive(receipt("TEST-PROFILE-001", "Тестовый профиль", "Белый", "6000", "шт.", 50));

        assertEquals(1, materials.findAll().size());
        StockPosition position =
                stockPositions
                        .findByNaturalKey(
                                warehouseId,
                                cellA,
                                materials.findAll().get(0),
                                StockState.AVAILABLE)
                        .orElseThrow();
        assertEquals(StockQuantity.of(150), position.quantity());
    }

    @Test
    void differentColorCreatesSeparateMaterialReference() {
        receipts.receive(receipt("VEKA-103.211", "Профиль VEKA Softline", "Белый", "6000", "шт.", 10));
        receipts.receive(
                receipt("VEKA-103.211", "Профиль VEKA Softline", "Антрацит", "6000", "шт.", 20));

        assertEquals(2, materials.findAll().size());
    }

    @Test
    void differentSizeCreatesSeparateMaterialReference() {
        receipts.receive(receipt("VEKA-103.211", "Профиль", "Белый", "6000", "шт.", 10));
        receipts.receive(receipt("VEKA-103.211", "Профиль", "Белый", "3000", "шт.", 20));

        assertEquals(2, materials.findAll().size());
    }

    @Test
    void moveDoesNotCreateMaterialReference() {
        MaterialReference material =
                receipts
                        .receive(
                                receipt(
                                        "MOVE-MAT",
                                        "Move material",
                                        "Белый",
                                        "6000",
                                        "шт.",
                                        10))
                        .material();
        int countAfterReceipt = materials.findAll().size();

        moves.move(
                new MoveRequest(
                        material,
                        StockQuantity.of(5),
                        warehouseId,
                        cellA,
                        warehouseId,
                        cellB));

        assertEquals(countAfterReceipt, materials.findAll().size());
    }

    @Test
    void transferDoesNotCreateMaterialReference() {
        MaterialReference material =
                receipts
                        .receive(
                                receipt(
                                        "TRANSFER-MAT",
                                        "Transfer material",
                                        "Белый",
                                        "6000",
                                        "шт.",
                                        10))
                        .material();
        int countAfterReceipt = materials.findAll().size();
        WarehouseId destWarehouse = WarehouseId.generate();
        StorageCellId destCell = StorageCellId.generate();
        JdbcWarehouseCatalogRepository catalog = new JdbcWarehouseCatalogRepository(jdbc, CLOCK);
        catalog.save(Warehouse.of(destWarehouse, "WH-2", "Second", true));
        catalog.save(StorageCell.of(destCell, destWarehouse, "C-01", true));

        transfers.send(
                new TransferSendRequest(
                        material,
                        StockQuantity.of(3),
                        warehouseId,
                        cellA,
                        destWarehouse));
        transfers.receive(
                new TransferReceiveRequest(
                        material,
                        StockQuantity.of(3),
                        warehouseId,
                        cellA,
                        destWarehouse,
                        destCell));

        assertEquals(countAfterReceipt, materials.findAll().size());
        assertEquals(material.id(), materials.findById(material.id()).orElseThrow().id());
    }

    @Test
    void differentUnitOfMeasureCreatesSeparateMaterialReference() {
        receipts.receive(receipt("VEKA-103.211", "Профиль", "Белый", "6000 мм", "м.", 300));
        receipts.receive(receipt("VEKA-103.211", "Профиль", "Белый", "6000 мм", "шт.", 50));

        assertEquals(2, materials.findAll().size());
    }

    @Test
    void metersAliasIsNormalizedToCanonicalUnit() {
        receipts.receive(receipt("VEKA-103.211", "Профиль", "Белый", "6000 мм", "метры", 10));

        assertEquals(1, materials.findAll().size());
        assertEquals("м.", materials.findAll().get(0).unitOfMeasure());
    }

    @Test
    void unsupportedUnitOfMeasureIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> receipts.receive(receipt("VEKA-103.211", "Профиль", "Белый", "6000 мм", "футы", 10)));
        assertEquals(0, materials.findAll().size());
    }

    @Test
    void consumptionDoesNotCreateMaterialReference() {
        MaterialReference material =
                receipts
                        .receive(
                                receipt(
                                        "CONSUME-MAT",
                                        "Consume material",
                                        "Белый",
                                        "6000",
                                        "шт.",
                                        100))
                        .material();
        int countAfterReceipt = materials.findAll().size();

        consumptions.consume(
                new ConsumptionRequest(
                        material,
                        StockQuantity.of(10),
                        warehouseId,
                        cellA));

        assertEquals(countAfterReceipt, materials.findAll().size());
    }

    private ReceiptRequest receipt(
            String article,
            String name,
            String color,
            String size,
            String unit,
            int quantity) {
        return new ReceiptRequest(
                article,
                name,
                color,
                size,
                unit,
                StockQuantity.of(quantity),
                warehouseId,
                cellA);
    }
}
