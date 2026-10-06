package com.tmp.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.Login;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.SessionId;
import com.tmp.security.api.SessionSummary;
import com.tmp.security.api.UserId;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskKind;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskSource;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskState;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskView;
import com.tmp.warehouse.api.WarehouseDemandCommandApi;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.AcceptProductionDemandCommand;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.AcceptProductionDemandResult;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.ProductionDemandLine;
import com.tmp.warehouse.api.WarehouseDemandDerivedStatus;
import com.tmp.warehouse.api.WarehouseDemandQueryApi;
import com.tmp.warehouse.application.document.WarehouseTransferDocumentProcessor;
import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockPosition;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.StorageCell;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.persistence.JdbcAvailableStockAggregationQuery;
import com.tmp.warehouse.persistence.JdbcDemandTaskStateRepository;
import com.tmp.warehouse.persistence.JdbcWarehouseDemandFulfillmentReadQuery;
import com.tmp.warehouse.persistence.JdbcWarehouseDemandRepository;
import com.tmp.warehouse.security.WarehousePermissions;
import com.tmp.warehouse.testsupport.WarehouseIntegrationTestSupport;
import com.tmp.warehouse.testsupport.WarehouseJdbcTestSupport;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
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
 * B3B-3C1: Demand-backed PRODUCTION_MATERIAL_SUPPLY tasks in the derived Warehouse operational
 * inbox — visibility, duplicate prevention, informational assignment/takeover.
 */
@Testcontainers
class WarehouseDemandSupplyTaskInboxIntegrationTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-06T10:00:00Z"), ZoneOffset.UTC);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    private final AtomicReference<SessionSummary> session = new AtomicReference<>();
    private final AtomicReference<Set<PermissionId>> permissions = new AtomicReference<>(Set.of());

    private WarehouseIntegrationTestSupport.ApiBundle bundle;
    private DefaultWarehouseApi api;
    private WarehouseDemandCommandApi demandApi;
    private WarehouseDemandQueryApi demandQuery;
    private UUID sourceUser;
    private UUID productionOnlyUser;
    private UUID noResponsibilityUser;
    private UUID mixedUser;
    private WarehouseId productionDestination;
    private WarehouseId sourceWarehouse;
    private WarehouseId otherSource;
    private StorageCellId sourceCell;
    private MaterialReference materialA;
    private MaterialReference materialB;

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
        jdbc.update("DELETE FROM warehouse.demand_task_state");
        jdbc.update("DELETE FROM warehouse.warehouse_demand_transfer_links");
        jdbc.update("DELETE FROM warehouse.warehouse_demand_lines");
        jdbc.update("DELETE FROM warehouse.warehouse_demands");
        jdbc.update("DELETE FROM warehouse.transfer_return_settlement_item");
        jdbc.update("DELETE FROM warehouse.transfer_receipt_settlement_item");
        jdbc.update("DELETE FROM warehouse.transfer_document_settlement");
        jdbc.update("DELETE FROM warehouse.transfer_document_send_allocation");
        jdbc.update("DELETE FROM warehouse.transfer_task_state");
        jdbc.update("DELETE FROM warehouse.transfer_document_lines");
        jdbc.update("DELETE FROM warehouse.transfer_document_payload");
        jdbc.update("DELETE FROM documents.document_lifecycle_journal");
        jdbc.update("DELETE FROM documents.document_versions");
        jdbc.update("DELETE FROM documents.documents");
        jdbc.update("DELETE FROM warehouse.warehouse_user_responsibility");
        jdbc.update("DELETE FROM warehouse.transfer_operation_context");
        jdbc.update("DELETE FROM warehouse.warehouse_movements");
        jdbc.update("DELETE FROM warehouse.warehouse_operations");
        jdbc.update("DELETE FROM warehouse.stock_positions");
        jdbc.update("DELETE FROM warehouse.storage_cells");
        jdbc.update("DELETE FROM warehouse.warehouses");
        jdbc.update("DELETE FROM warehouse.material_references");

        sourceUser = UUID.randomUUID();
        productionOnlyUser = UUID.randomUUID();
        noResponsibilityUser = UUID.randomUUID();
        mixedUser = UUID.randomUUID();
        session.set(sessionFor(sourceUser));
        grantFullWarehousePermissions();

        bundle =
                WarehouseIntegrationTestSupport.createApiBundle(
                        dataSource,
                        CLOCK,
                        authorizationFromPermissions(),
                        authenticationFromSession(),
                        new DefaultWarehouseResponsibilityGuard(
                                authenticationFromSession(),
                                new com.tmp.warehouse.persistence
                                        .JdbcWarehouseUserResponsibilityRepository(jdbc, CLOCK)));
        api = bundle.api();
        if (bundle.documentEngine().registeredTypes().stream()
                .noneMatch(
                        t ->
                                WarehouseTransferDocumentProcessor.DOCUMENT_TYPE_ID.equals(
                                        t.typeId()))) {
            bundle.documentEngine()
                    .registerProcessor(
                            new WarehouseTransferDocumentProcessor(bundle.transferDocuments()));
        }
        JdbcWarehouseDemandRepository demandRepository = new JdbcWarehouseDemandRepository(jdbc);
        JdbcWarehouseDemandFulfillmentReadQuery fulfillment =
                new JdbcWarehouseDemandFulfillmentReadQuery(jdbc);
        demandApi =
                new DefaultWarehouseDemandCommandApi(
                        new MaterialSourceRoutingService(
                                new JdbcAvailableStockAggregationQuery(jdbc)),
                        bundle.transferDocumentService(),
                        bundle.catalog(),
                        bundle.materials(),
                        demandRepository,
                        bundle.transferDocuments(),
                        fulfillment,
                        new JdbcDemandTaskStateRepository(jdbc, CLOCK),
                        CLOCK,
                        new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
        demandQuery = new DefaultWarehouseDemandQueryApi(demandRepository, fulfillment);

        productionDestination = WarehouseId.generate();
        sourceWarehouse = WarehouseId.generate();
        otherSource = WarehouseId.generate();
        bundle.catalog()
                .save(
                        Warehouse.of(
                                productionDestination, "PROD", "Production WH", true, true));
        bundle.catalog().save(Warehouse.create(sourceWarehouse, "SRC", "Source WH"));
        bundle.catalog().save(Warehouse.create(otherSource, "SRC2", "Source 2"));
        sourceCell = StorageCellId.generate();
        bundle.catalog().save(StorageCell.create(sourceCell, sourceWarehouse, "S-1"));
        materialA =
                WarehouseJdbcTestSupport.persistMaterial(
                        jdbc,
                        CLOCK,
                        MaterialReference.create("SUP-A", "Supply A", "", "", "шт."));
        materialB =
                WarehouseJdbcTestSupport.persistMaterial(
                        jdbc,
                        CLOCK,
                        MaterialReference.create("SUP-B", "Supply B", "", "", "шт."));

        api.assignUserToWarehouse(sourceWarehouse.value(), sourceUser);
        api.assignUserToWarehouse(productionDestination.value(), productionOnlyUser);
        api.assignUserToWarehouse(sourceWarehouse.value(), mixedUser);
        api.assignUserToWarehouse(productionDestination.value(), mixedUser);
    }

    @Test
    void unmatchedDemandShowsSupplyTaskToNonProductionUserNotProductionOnly() {
        AcceptProductionDemandResult result =
                demandApi.acceptProductionDemand(
                        command(
                                unmatchedLine(
                                        UUID.randomUUID(), "UNKNOWN-SUP", "Unknown", "5")));

        session.set(sessionFor(sourceUser));
        List<WarehouseTaskView> sourceTasks = api.listMyWarehouseTasks(null);
        assertEquals(1, sourceTasks.size());
        WarehouseTaskView supply = sourceTasks.getFirst();
        assertEquals(WarehouseTaskKind.PRODUCTION_MATERIAL_SUPPLY, supply.taskKind());
        assertEquals(WarehouseTaskSource.WAREHOUSE_DEMAND, supply.taskSource());
        assertEquals(result.demandId(), supply.demandId());
        assertNull(supply.documentId());
        assertNull(supply.sourceWarehouseId());
        assertEquals(productionDestination.value(), supply.destinationWarehouseId());
        assertEquals(WarehouseTaskState.NEW, supply.taskState());
        assertEquals(1, supply.lineCount());

        session.set(sessionFor(productionOnlyUser));
        assertTrue(
                api.listMyWarehouseTasks(null).stream()
                        .noneMatch(
                                t ->
                                        t.taskKind()
                                                == WarehouseTaskKind.PRODUCTION_MATERIAL_SUPPLY));
    }

    @Test
    void zeroStockShowsSupplyTaskWithWaitingReasonInDetailProjection() {
        AcceptProductionDemandResult result =
                demandApi.acceptProductionDemand(
                        command(line(UUID.randomUUID(), materialA, "10")));
        assertTrue(result.documents().isEmpty());

        session.set(sessionFor(sourceUser));
        List<WarehouseTaskView> tasks = api.listMyWarehouseTasks(null);
        assertEquals(1, tasks.size());
        assertEquals(WarehouseTaskKind.PRODUCTION_MATERIAL_SUPPLY, tasks.getFirst().taskKind());

        var details = demandQuery.getDemandSupplyTask(result.demandId());
        assertTrue(details.isPresent());
        assertEquals(1, details.get().waitingLines().size());
        assertEquals(
                "NO_AVAILABLE_STOCK",
                details.get().waitingLines().getFirst().effectiveWaitingReason());
    }

    @Test
    void routableDemandShowsPreparationNotSupply() {
        seedAvailable(materialA, "50");
        AcceptProductionDemandResult result =
                demandApi.acceptProductionDemand(
                        command(line(UUID.randomUUID(), materialA, "10")));
        assertEquals(1, result.documents().size());

        session.set(sessionFor(sourceUser));
        List<WarehouseTaskView> tasks = api.listMyWarehouseTasks(null);
        assertEquals(1, tasks.size());
        assertEquals(WarehouseTaskKind.TRANSFER_PREPARATION, tasks.getFirst().taskKind());
        assertEquals(result.documents().getFirst().documentId(), tasks.getFirst().documentId());
        assertTrue(
                tasks.stream()
                        .noneMatch(
                                t ->
                                        t.taskKind()
                                                == WarehouseTaskKind.PRODUCTION_MATERIAL_SUPPLY));
        assertTrue(demandQuery.getDemandSupplyTask(result.demandId()).isEmpty());
    }

    @Test
    void mixedDemandShowsPreparationForRoutedAndOneSupplyForWaitingSibling() {
        seedAvailable(materialA, "50");
        AcceptProductionDemandResult result =
                demandApi.acceptProductionDemand(
                        command(
                                line(UUID.randomUUID(), materialA, "10"),
                                line(UUID.randomUUID(), materialB, "7")));
        assertEquals(1, result.documents().size());

        session.set(sessionFor(sourceUser));
        List<WarehouseTaskView> tasks = api.listMyWarehouseTasks(null);
        assertEquals(2, tasks.size());
        assertTrue(
                tasks.stream()
                        .anyMatch(t -> t.taskKind() == WarehouseTaskKind.TRANSFER_PREPARATION));
        List<WarehouseTaskView> supply =
                tasks.stream()
                        .filter(
                                t ->
                                        t.taskKind()
                                                == WarehouseTaskKind.PRODUCTION_MATERIAL_SUPPLY)
                        .toList();
        assertEquals(1, supply.size());
        assertEquals(result.demandId(), supply.getFirst().demandId());
        assertEquals(1, supply.getFirst().lineCount());

        var details = demandQuery.getDemandSupplyTask(result.demandId()).orElseThrow();
        assertEquals(1, details.waitingLines().size());
        assertEquals(materialB.article(), details.waitingLines().getFirst().materialCode());
    }

    @Test
    void fulfilledDemandHasNoSupplyTask() {
        seedAvailable(materialA, "50");
        AcceptProductionDemandResult result =
                demandApi.acceptProductionDemand(
                        command(line(UUID.randomUUID(), materialA, "10")));
        assertTrue(demandQuery.getDemandSupplyTask(result.demandId()).isEmpty());
        session.set(sessionFor(sourceUser));
        assertTrue(
                api.listMyWarehouseTasks(null).stream()
                        .noneMatch(
                                t ->
                                        t.taskKind()
                                                == WarehouseTaskKind.PRODUCTION_MATERIAL_SUPPLY));
    }

    @Test
    void takeInWorkAndTakeoverAreInformationalOnly() {
        AcceptProductionDemandResult result =
                demandApi.acceptProductionDemand(
                        command(
                                unmatchedLine(
                                        UUID.randomUUID(), "UNKNOWN-T", "Unknown", "3")));
        UUID demandId = result.demandId();

        session.set(sessionFor(sourceUser));
        WarehouseTaskView taken = api.takeDemandSupplyTaskInWork(demandId);
        assertEquals(WarehouseTaskState.IN_WORK, taken.taskState());
        assertEquals(sourceUser, taken.workingUserId());
        assertEquals(0, count("warehouse.transfer_document_payload"));

        session.set(sessionFor(mixedUser));
        WarehouseTaskView takeover = api.takeDemandSupplyTaskInWork(demandId);
        assertEquals(WarehouseTaskState.IN_WORK, takeover.taskState());
        assertEquals(mixedUser, takeover.workingUserId());
        assertEquals(0, count("warehouse.transfer_document_payload"));

        var demand = demandQuery.getDemand(demandId).orElseThrow();
        assertEquals(WarehouseDemandDerivedStatus.WAITING_FOR_SUPPLY, demand.derivedStatus());
    }

    @Test
    void accessRulesForResponsibilityScopes() {
        demandApi.acceptProductionDemand(
                command(unmatchedLine(UUID.randomUUID(), "UNKNOWN-A", "Unknown", "1")));

        session.set(sessionFor(sourceUser));
        assertEquals(1, countSupply(api.listMyWarehouseTasks(null)));

        session.set(sessionFor(mixedUser));
        assertEquals(1, countSupply(api.listMyWarehouseTasks(null)));

        session.set(sessionFor(productionOnlyUser));
        assertEquals(0, countSupply(api.listMyWarehouseTasks(null)));

        session.set(sessionFor(noResponsibilityUser));
        assertTrue(api.listMyWarehouseTasks(null).isEmpty());
    }

    @Test
    void productionOnlyUserCannotTakeSupplyTask() {
        AcceptProductionDemandResult result =
                demandApi.acceptProductionDemand(
                        command(unmatchedLine(UUID.randomUUID(), "UNKNOWN-B", "Unknown", "1")));
        session.set(sessionFor(productionOnlyUser));
        assertThrows(
                AccessDeniedException.class,
                () -> api.takeDemandSupplyTaskInWork(result.demandId()));
    }

    @Test
    void flywayV52DemandTaskStateExists() {
        Integer applied =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM flyway_schema_history
                         WHERE version = '52' AND success = TRUE
                        """,
                        Integer.class);
        assertEquals(1, applied);
        Integer tables =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.tables
                         WHERE table_schema = 'warehouse' AND table_name = 'demand_task_state'
                        """,
                        Integer.class);
        assertEquals(1, tables);
    }

    private static long countSupply(List<WarehouseTaskView> tasks) {
        return tasks.stream()
                .filter(t -> t.taskKind() == WarehouseTaskKind.PRODUCTION_MATERIAL_SUPPLY)
                .count();
    }

    private void seedAvailable(MaterialReference material, String qty) {
        bundle.stockPositions()
                .create(
                        StockPosition.of(
                                sourceWarehouse,
                                sourceCell,
                                material,
                                StockState.AVAILABLE,
                                StockQuantity.of(new BigDecimal(qty))));
    }

    private AcceptProductionDemandCommand command(ProductionDemandLine... lines) {
        return new AcceptProductionDemandCommand(
                UUID.randomUUID(),
                productionDestination.value(),
                "system",
                List.of(lines));
    }

    private static ProductionDemandLine line(
            UUID sourceLineId, MaterialReference material, String qty) {
        return new ProductionDemandLine(
                sourceLineId,
                material.article(),
                material.name(),
                material.color(),
                material.unitOfMeasure(),
                null,
                new BigDecimal(qty),
                material.id().value());
    }

    private static ProductionDemandLine unmatchedLine(
            UUID sourceLineId, String article, String name, String qty) {
        return new ProductionDemandLine(
                sourceLineId, article, name, "", "шт.", null, new BigDecimal(qty), null);
    }

    private int count(String table) {
        Integer value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        assertNotNull(value);
        return value;
    }

    private void grantFullWarehousePermissions() {
        permissions.set(
                Set.of(
                        WarehousePermissions.WAREHOUSE_VIEW,
                        WarehousePermissions.WAREHOUSE_TRANSFER,
                        WarehousePermissions.WAREHOUSE_STRUCTURE_CREATE,
                        WarehousePermissions.WAREHOUSE_STRUCTURE_UPDATE,
                        WarehousePermissions.WAREHOUSE_STRUCTURE_VIEW,
                        WarehousePermissions.STORAGE_CELL_CREATE));
    }

    private AuthorizationService authorizationFromPermissions() {
        return new AuthorizationService() {
            @Override
            public boolean hasPermission(PermissionId permissionId) {
                return permissions.get().contains(permissionId);
            }

            @Override
            public void requirePermission(PermissionId permissionId) {
                if (!hasPermission(permissionId)) {
                    throw new AccessDeniedException("denied: " + permissionId.value());
                }
            }

            @Override
            public Set<PermissionId> effectivePermissions() {
                return permissions.get();
            }
        };
    }

    private AuthenticationService authenticationFromSession() {
        return new AuthenticationService() {
            @Override
            public SessionSummary login(Login login, char[] password) {
                throw new UnsupportedOperationException();
            }

            @Override
            public SessionSummary completePasswordSetup(
                    Login login, String activationCode, char[] newPassword, char[] confirmPassword) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void logout() {}

            @Override
            public Optional<SessionSummary> currentSession() {
                return Optional.ofNullable(session.get());
            }

            @Override
            public boolean isAuthenticated() {
                return session.get() != null;
            }
        };
    }

    private static SessionSummary sessionFor(UUID userId) {
        return new SessionSummary(
                SessionId.of(UUID.randomUUID()),
                UserId.of(userId),
                Login.of("user-" + userId.toString().substring(0, 8)),
                Instant.parse("2026-10-06T10:00:00Z"));
    }
}
