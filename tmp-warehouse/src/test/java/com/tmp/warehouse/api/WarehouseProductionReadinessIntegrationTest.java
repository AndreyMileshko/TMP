package com.tmp.warehouse.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.warehouse.api.WarehouseApi.AvailabilityStatus;
import com.tmp.warehouse.api.WarehouseApi.MaterialIdentityRequest;
import com.tmp.warehouse.api.WarehouseApi.OperationKind;
import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockPosition;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.StorageCell;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.testsupport.WarehouseIntegrationTestSupport;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

/** STAGE7-000B: Warehouse query/command boundaries and material identity readiness. */
@Testcontainers
class WarehouseProductionReadinessIntegrationTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-17T08:00:00Z"), ZoneOffset.UTC);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private static WarehouseIntegrationTestSupport.ApiBundle bundle;

    private WarehouseQueryApi queryApi;
    private WarehouseCommandApi commandApi;
    private JdbcTemplate jdbc;

    private WarehouseId mainWarehouseId;
    private WarehouseId productionWarehouseId;
    private StorageCellId mainCellId;
    private StorageCellId productionCellId;

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
        bundle = WarehouseIntegrationTestSupport.createApiBundle(dataSource, CLOCK);
    }

    @BeforeEach
    void setUp() {
        jdbc = bundle.jdbc();
        jdbc.update("DELETE FROM warehouse.transfer_operation_context");
        jdbc.update("DELETE FROM warehouse.warehouse_movements");
        jdbc.update("DELETE FROM warehouse.warehouse_operations");
        jdbc.update("DELETE FROM warehouse.stock_positions");
        jdbc.update("DELETE FROM warehouse.storage_cells");
        jdbc.update("DELETE FROM warehouse.warehouses");
        jdbc.update("DELETE FROM warehouse.material_references");

        bundle = WarehouseIntegrationTestSupport.createApiBundle(dataSource, CLOCK);
        queryApi = bundle.api();
        commandApi = bundle.api();

        mainWarehouseId = WarehouseId.generate();
        productionWarehouseId = WarehouseId.generate();
        mainCellId = StorageCellId.generate();
        productionCellId = StorageCellId.generate();
        bundle.catalog().save(Warehouse.create(mainWarehouseId, "WH-MAIN", "Main"));
        bundle.catalog().save(Warehouse.create(productionWarehouseId, "WH-PROD", "Production"));
        bundle.catalog().save(StorageCell.create(mainCellId, mainWarehouseId, "M-01"));
        bundle.catalog().save(StorageCell.create(productionCellId, productionWarehouseId, "P-01"));
    }

    @Test
    void receiptThenExactIdentityAvailabilityMatchesStock() {
        commandApi.receive(
                new WarehouseApi.ReceiptCommand(
                        "ALU-6060",
                        "ALU profile",
                        "Silver",
                        "6000",
                        "шт.",
                        BigDecimal.valueOf(42),
                        mainWarehouseId.value(),
                        mainCellId.value()));

        MaterialIdentityRequest identity =
                MaterialIdentityRequest.of("ALU-6060", "Silver", "6000", "шт.");
        WarehouseApi.AvailabilityResult result =
                queryApi.checkAvailability(identity, BigDecimal.valueOf(40));

        assertEquals(AvailabilityStatus.AVAILABLE, result.status());
        assertEquals(0, result.availableQuantity().compareTo(BigDecimal.valueOf(42)));
    }

    @Test
    void variantIsolationDoesNotMixStockByArticleOnly() {
        MaterialReference variantA =
                bundle.materials()
                        .create(
                                MaterialReference.create(
                                        "ALU-6060", "A", "Silver", "6000", "шт."));
        MaterialReference variantB =
                bundle.materials()
                        .create(
                                MaterialReference.create(
                                        "ALU-6060", "B", "White", "3000", "м."));
        bundle.stockPositions()
                .create(
                        StockPosition.of(
                                mainWarehouseId,
                                mainCellId,
                                variantA,
                                StockState.AVAILABLE,
                                StockQuantity.of(10L)));
        bundle.stockPositions()
                .create(
                        StockPosition.of(
                                mainWarehouseId,
                                mainCellId,
                                variantB,
                                StockState.AVAILABLE,
                                StockQuantity.of(99L)));

        WarehouseApi.AvailabilityResult forA =
                queryApi.checkAvailability(
                        MaterialIdentityRequest.of("ALU-6060", "Silver", "6000", "шт."),
                        BigDecimal.valueOf(10));
        WarehouseApi.AvailabilityResult forB =
                queryApi.checkAvailability(
                        MaterialIdentityRequest.of("ALU-6060", "White", "3000", "м."),
                        BigDecimal.valueOf(50));

        assertEquals(AvailabilityStatus.AVAILABLE, forA.status());
        assertEquals(0, forA.availableQuantity().compareTo(BigDecimal.valueOf(10)));
        assertEquals(AvailabilityStatus.AVAILABLE, forB.status());
        assertEquals(0, forB.availableQuantity().compareTo(BigDecimal.valueOf(99)));
    }

    @Test
    void warehouseScopedAvailabilitySeparatesStockByWarehouse() {
        MaterialReference material =
                bundle.materials()
                        .create(MaterialReference.create("STEEL-1", "Steel", "", "", "шт."));
        bundle.stockPositions()
                .create(
                        StockPosition.of(
                                mainWarehouseId,
                                mainCellId,
                                material,
                                StockState.AVAILABLE,
                                StockQuantity.of(30L)));
        bundle.stockPositions()
                .create(
                        StockPosition.of(
                                productionWarehouseId,
                                productionCellId,
                                material,
                                StockState.AVAILABLE,
                                StockQuantity.of(7L)));

        WarehouseApi.AvailabilityResult mainOnly =
                queryApi.checkAvailability(
                        material.id().value(), mainWarehouseId.value(), BigDecimal.valueOf(25));
        WarehouseApi.AvailabilityResult prodOnly =
                queryApi.checkAvailability(
                        material.id().value(),
                        productionWarehouseId.value(),
                        BigDecimal.valueOf(5));

        assertEquals(AvailabilityStatus.AVAILABLE, mainOnly.status());
        assertEquals(0, mainOnly.availableQuantity().compareTo(BigDecimal.valueOf(30)));
        assertEquals(AvailabilityStatus.AVAILABLE, prodOnly.status());
        assertEquals(0, prodOnly.availableQuantity().compareTo(BigDecimal.valueOf(7)));
    }

    @Test
    void legacyArticleAvailabilityRemainsSeparateFromExactIdentityPath() {
        MaterialReference legacy = bundle.materials().create(MaterialReference.legacyArticle("LEG-1"));
        bundle.stockPositions()
                .create(
                        StockPosition.of(
                                mainWarehouseId,
                                mainCellId,
                                legacy,
                                StockState.AVAILABLE,
                                StockQuantity.of(5L)));
        bundle.materials()
                .create(MaterialReference.create("LEG-1", "New", "Red", "1000", "шт."));

        WarehouseApi.AvailabilityResult legacyResult =
                queryApi.checkAvailabilityByLegacyArticle("LEG-1", BigDecimal.valueOf(5));
        assertEquals(AvailabilityStatus.AVAILABLE, legacyResult.status());

        WarehouseApi.AvailabilityResult exactIdentityResult =
                queryApi.checkAvailability(
                        MaterialIdentityRequest.of("LEG-1", "Red", "1000", "шт."),
                        BigDecimal.ONE);
        assertEquals(AvailabilityStatus.INSUFFICIENT, exactIdentityResult.status());
        assertEquals(0, exactIdentityResult.availableQuantity().compareTo(BigDecimal.ZERO));
    }

    @Test
    void transferDraftDoesNotMutateStockUntilSendThenReceive() {
        MaterialReference material =
                bundle.materials()
                        .create(MaterialReference.create("TR-1", "Transfer mat", "", "", "шт."));
        bundle.stockPositions()
                .create(
                        StockPosition.of(
                                mainWarehouseId,
                                mainCellId,
                                material,
                                StockState.AVAILABLE,
                                StockQuantity.of(20L)));

        WarehouseApi.TransferRequestView draft =
                commandApi.createTransferDraft(
                        new WarehouseApi.CreateTransferDraftCommand(
                                material.id().value(),
                                BigDecimal.valueOf(8),
                                mainWarehouseId.value(),
                                mainCellId.value(),
                                productionWarehouseId.value(),
                                productionCellId.value()));

        assertEquals("DRAFT", draft.status());
        assertEquals(
                0,
                queryApi
                        .checkAvailability(material.id().value(), BigDecimal.valueOf(20))
                        .availableQuantity()
                        .compareTo(BigDecimal.valueOf(20)));

        WarehouseApi.OperationResult sent = commandApi.sendTransfer(draft.operationId());
        assertEquals(OperationKind.TRANSFER_SEND, sent.kind());
        assertEquals(
                0,
                queryApi
                        .checkAvailability(material.id().value(), mainWarehouseId.value(), BigDecimal.valueOf(12))
                        .availableQuantity()
                        .compareTo(BigDecimal.valueOf(12)));

        WarehouseApi.OperationResult received = commandApi.receiveTransfer(sent.operationId());
        assertEquals(OperationKind.TRANSFER_RECEIVE, received.kind());
        assertEquals(
                0,
                queryApi
                        .checkAvailability(
                                material.id().value(),
                                productionWarehouseId.value(),
                                BigDecimal.valueOf(8))
                        .availableQuantity()
                        .compareTo(BigDecimal.valueOf(8)));
    }

    @Test
    void consumptionJoinsOuterTransactionAndRollsBackWithAmbientTransaction() {
        MaterialReference material =
                bundle.materials()
                        .create(MaterialReference.create("CON-1", "Consume", "", "", "шт."));
        bundle.stockPositions()
                .create(
                        StockPosition.of(
                                mainWarehouseId,
                                mainCellId,
                                material,
                                StockState.AVAILABLE,
                                StockQuantity.of(50L)));

        TransactionTemplate outer = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        RuntimeException failure =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                outer.executeWithoutResult(
                                        status -> {
                                            commandApi.consume(
                                                    new WarehouseApi.ConsumptionCommand(
                                                            material.id().value(),
                                                            BigDecimal.valueOf(10),
                                                            mainWarehouseId.value(),
                                                            mainCellId.value()));
                                            throw new RuntimeException("rollback probe");
                                        }));

        assertEquals("rollback probe", failure.getMessage());
        assertEquals(
                0,
                queryApi
                        .checkAvailability(material.id().value(), BigDecimal.valueOf(50))
                        .availableQuantity()
                        .compareTo(BigDecimal.valueOf(50)));
    }

    @Test
    void consumptionCommitsWithOuterTransaction() {
        MaterialReference material =
                bundle.materials()
                        .create(MaterialReference.create("CON-2", "Consume", "", "", "шт."));
        bundle.stockPositions()
                .create(
                        StockPosition.of(
                                mainWarehouseId,
                                mainCellId,
                                material,
                                StockState.AVAILABLE,
                                StockQuantity.of(50L)));

        TransactionTemplate outer = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        outer.executeWithoutResult(
                status ->
                        commandApi.consume(
                                new WarehouseApi.ConsumptionCommand(
                                        material.id().value(),
                                        BigDecimal.valueOf(10),
                                        mainWarehouseId.value(),
                                        mainCellId.value())));

        assertTrue(
                queryApi.checkAvailability(material.id().value(), BigDecimal.valueOf(40)).isAvailable());
    }
}
