package com.tmp.warehouse.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.warehouse.api.WarehouseApi.AvailabilityStatus;
import com.tmp.warehouse.api.WarehouseApi.CreateReservationLinkCommand;
import com.tmp.warehouse.api.WarehouseApi.ExecuteOperationCommand;
import com.tmp.warehouse.api.WarehouseApi.OperationKind;
import com.tmp.warehouse.api.WarehouseApi.ReservationTargetTypeView;
import com.tmp.warehouse.api.WarehouseApi.StockStateView;
import com.tmp.warehouse.api.WarehouseApi.StockView;
import com.tmp.warehouse.application.DefaultWarehouseApi;
import com.tmp.warehouse.application.FixedMaterialReferenceDisplayPort;
import com.tmp.warehouse.application.WarehouseAdjustmentService;
import com.tmp.warehouse.application.WarehouseConsumptionService;
import com.tmp.warehouse.application.WarehouseMoveService;
import com.tmp.warehouse.application.WarehouseOperationEngine;
import com.tmp.warehouse.application.WarehouseReceiptService;
import com.tmp.warehouse.application.WarehouseReservationLinkService;
import com.tmp.warehouse.application.WarehouseTransferService;
import com.tmp.warehouse.domain.StorageCell;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.repository.MaterialReferenceRepository;
import com.tmp.warehouse.domain.repository.StockPositionRepository;
import com.tmp.warehouse.persistence.JdbcMaterialReservationLinkRepository;
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
import java.util.Set;
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
 * Integration: Public API calls persist correctly and return public DTOs.
 */
@Testcontainers
class WarehouseApiIntegrationTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-09T16:30:00Z"), ZoneOffset.UTC);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    private JdbcWarehouseCatalogRepository catalog;
    private MaterialReferenceRepository materials;
    private StockPositionRepository stockPositions;
    private WarehouseOperationRepository operations;
    private WarehouseMovementRepository movements;
    private WarehouseApi api;

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
        jdbc.update("DELETE FROM warehouse.material_reservation_links");
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
        api =
                new DefaultWarehouseApi(
                        AllowingAuthorization.INSTANCE,
                        catalog,
                        stockPositions,
                        materials,
                        new FixedMaterialReferenceDisplayPort(),
                        new WarehouseReservationLinkService(
                                new JdbcMaterialReservationLinkRepository(jdbc), CLOCK),
                        new WarehouseReceiptService(engine, stockPositions, materials),
                        new WarehouseMoveService(engine),
                        new WarehouseTransferService(engine),
                        new WarehouseConsumptionService(engine, stockPositions),
                        new WarehouseAdjustmentService(engine, stockPositions));
    }

    private enum AllowingAuthorization implements AuthorizationService {
        INSTANCE;

        @Override
        public boolean hasPermission(PermissionId permissionId) {
            return true;
        }

        @Override
        public void requirePermission(PermissionId permissionId) {
            // allow all for Public API persistence integration tests
        }

        @Override
        public Set<PermissionId> effectivePermissions() {
            return Set.of();
        }
    }

    @Test
    void receiptThenGetStockAndAvailabilityThroughPublicApi() {
        WarehouseId warehouseId = WarehouseId.generate();
        StorageCellId cellId = StorageCellId.generate();
        catalog.insert(Warehouse.create(warehouseId, "WH-API-1", "API Warehouse"));
        catalog.insert(StorageCell.create(cellId, warehouseId, "A-01"));

        WarehouseApi.OperationResult receipt =
                api.executeWarehouseOperation(
                        ExecuteOperationCommand.receipt(
                                "ALU-6060",
                                "ALU-6060",
                                "",
                                "",
                                "шт.",
                                BigDecimal.valueOf(100),
                                warehouseId.value(),
                                cellId.value()));

        assertEquals(OperationKind.RECEIPT, receipt.kind());
        assertEquals("COMPLETED", receipt.status());
        assertEquals(
                WarehouseApi.OperationKind.RECEIPT.name(),
                operations.findById(
                                com.tmp.warehouse.domain.WarehouseOperationId.of(
                                        receipt.operationId()))
                        .orElseThrow()
                        .type()
                        .name());

        List<StockView> stock = api.getStock("ALU-6060");
        assertEquals(1, stock.size());
        assertEquals(StockStateView.AVAILABLE, stock.get(0).stockState());
        assertEquals(0, stock.get(0).quantity().compareTo(BigDecimal.valueOf(100)));

        WarehouseApi.AvailabilityResult available =
                api.checkAvailability("ALU-6060", BigDecimal.valueOf(40));
        assertEquals(AvailabilityStatus.AVAILABLE, available.status());

        WarehouseApi.AvailabilityResult insufficient =
                api.checkAvailability("ALU-6060", BigDecimal.valueOf(150));
        assertEquals(AvailabilityStatus.INSUFFICIENT, insufficient.status());
    }

    @Test
    void createReservationLinkDoesNotTouchStockOrMovements() {
        var material =
                materials.create(com.tmp.warehouse.domain.MaterialReference.legacyArticle("VEKA 103.211"));
        WarehouseApi.ReservationLinkView link =
                api.createReservationLink(
                        new CreateReservationLinkCommand(
                                material.id().value(),
                                ReservationTargetTypeView.ORDER,
                                "26096190",
                                BigDecimal.valueOf(200)));

        assertEquals("VEKA 103.211", link.materialCode());
        assertEquals(ReservationTargetTypeView.ORDER, link.targetType());

        Integer stockRows =
                jdbc.queryForObject("SELECT COUNT(*) FROM warehouse.stock_positions", Integer.class);
        Integer movementRows =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.warehouse_movements", Integer.class);
        Integer linkRows =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.material_reservation_links", Integer.class);
        assertEquals(0, stockRows);
        assertEquals(0, movementRows);
        assertEquals(1, linkRows);
        assertTrue(linkRows > 0);
    }

    @Test
    void consumptionThroughPublicApiUpdatesStock() {
        WarehouseId warehouseId = WarehouseId.generate();
        StorageCellId cellId = StorageCellId.generate();
        catalog.insert(Warehouse.create(warehouseId, "WH-API-2", "API Consume"));
        catalog.insert(StorageCell.create(cellId, warehouseId, "C-01"));

        WarehouseApi.OperationResult receipt =
                api.executeWarehouseOperation(
                        ExecuteOperationCommand.receipt(
                                "ALU-6060",
                                "ALU-6060",
                                "",
                                "",
                                "шт.",
                                BigDecimal.valueOf(80),
                                warehouseId.value(),
                                cellId.value()));

        WarehouseApi.OperationResult consumed =
                api.executeWarehouseOperation(
                        ExecuteOperationCommand.consumption(
                                receipt.materialReferenceId(),
                                BigDecimal.valueOf(30),
                                warehouseId.value(),
                                cellId.value()));

        assertEquals(OperationKind.CONSUMPTION, consumed.kind());
        assertEquals("COMPLETED", consumed.status());
        assertEquals(
                0,
                api.getStock("ALU-6060").get(0).quantity().compareTo(BigDecimal.valueOf(50)));
    }

    @Test
    void createWarehouseAndStorageCellThroughPublicApi() {
        WarehouseApi.WarehouseView warehouse =
                api.createWarehouse(
                        new WarehouseApi.CreateWarehouseCommand("WH-API-3", "Created", true));
        assertEquals("WH-API-3", warehouse.code());
        assertTrue(warehouse.active());

        WarehouseApi.StorageCellView cell =
                api.createStorageCell(
                        new WarehouseApi.CreateStorageCellCommand(
                                warehouse.warehouseId(), "Z-01", true));
        assertEquals("Z-01", cell.code());
        assertEquals(warehouse.warehouseId(), cell.warehouseId());

        List<WarehouseApi.StorageCellView> cells =
                api.listStorageCells(warehouse.warehouseId());
        assertEquals(1, cells.size());
        assertEquals("Z-01", cells.get(0).code());

        List<WarehouseApi.WarehouseView> warehouses = api.listWarehouses();
        assertTrue(warehouses.stream().anyMatch(view -> view.code().equals("WH-API-3")));
    }
}
