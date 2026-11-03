package com.tmp.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.document.api.DocumentStatus;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.Login;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.SessionId;
import com.tmp.security.api.SessionSummary;
import com.tmp.security.api.UserId;
import com.tmp.warehouse.api.WarehouseApi.RejectTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.ReturnTransferMaterialsCommand;
import com.tmp.warehouse.api.WarehouseApi.SendTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentRejectResult;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentSourceAllocationInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentView;
import com.tmp.warehouse.api.WarehouseDemandCommandApi;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.AcceptProductionDemandCommand;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.AcceptProductionDemandResult;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.ProductionDemandLine;
import com.tmp.warehouse.api.WarehouseDemandDerivedStatus;
import com.tmp.warehouse.api.WarehouseDemandQueryApi;
import com.tmp.warehouse.api.WarehouseDemandQueryApi.WarehouseDemandLineView;
import com.tmp.warehouse.application.document.WarehouseTransferDocumentProcessor;
import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockPosition;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.StorageCell;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.TransferSettlementState;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.persistence.JdbcAvailableStockAggregationQuery;
import com.tmp.warehouse.persistence.JdbcDemandTaskStateRepository;
import com.tmp.warehouse.persistence.JdbcWarehouseDemandFulfillmentReadQuery;
import com.tmp.warehouse.persistence.JdbcWarehouseDemandRepository;
import com.tmp.warehouse.persistence.JdbcWarehouseDemandResupplyWaiverRepository;
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

@Testcontainers
class WarehouseDemandResupplyWaiverIntegrationTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC);

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
    private UUID worker;
    private WarehouseId destination;
    private WarehouseId source;
    private StorageCellId sourceCell;
    private MaterialReference materialA;

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
        jdbc.update("DELETE FROM warehouse.warehouse_demand_resupply_waivers");
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

        worker = UUID.randomUUID();
        session.set(sessionFor(worker));
        permissions.set(
                Set.of(
                        WarehousePermissions.WAREHOUSE_TRANSFER,
                        WarehousePermissions.WAREHOUSE_VIEW,
                        WarehousePermissions.WAREHOUSE_STRUCTURE_CREATE,
                        WarehousePermissions.WAREHOUSE_STRUCTURE_UPDATE,
                        WarehousePermissions.WAREHOUSE_STRUCTURE_VIEW,
                        WarehousePermissions.STORAGE_CELL_CREATE));

        bundle =
                WarehouseIntegrationTestSupport.createApiBundle(
                        dataSource,
                        CLOCK,
                        authorizationFromPermissions(),
                        authenticationFromSession(),
                        WarehouseIntegrationTestSupport.permitAllResponsibility());
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
        JdbcWarehouseDemandRepository demands = new JdbcWarehouseDemandRepository(jdbc);
        demandApi =
                new DefaultWarehouseDemandCommandApi(
                        new MaterialSourceRoutingService(
                                new JdbcAvailableStockAggregationQuery(jdbc)),
                        bundle.transferDocumentService(),
                        bundle.catalog(),
                        bundle.materials(),
                        demands,
                        bundle.transferDocuments(),
                        new JdbcWarehouseDemandFulfillmentReadQuery(jdbc),
                        new JdbcWarehouseDemandResupplyWaiverRepository(jdbc),
                        new JdbcDemandTaskStateRepository(jdbc, CLOCK),
                        CLOCK,
                        new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
        demandQuery =
                new DefaultWarehouseDemandQueryApi(
                        demands,
                        new JdbcWarehouseDemandFulfillmentReadQuery(jdbc),
                        new JdbcWarehouseDemandResupplyWaiverRepository(jdbc));

        destination = WarehouseId.generate();
        source = WarehouseId.generate();
        bundle.catalog().save(Warehouse.create(destination, "DEST", "Destination"));
        bundle.catalog().save(Warehouse.create(source, "SRC", "Source"));
        sourceCell = StorageCellId.generate();
        bundle.catalog().save(StorageCell.create(sourceCell, source, "S-1"));
        materialA =
                WarehouseJdbcTestSupport.persistMaterial(
                        jdbc, CLOCK, MaterialReference.create("ART-A", "Art A", "", "", "шт."));
        api.assignUserToWarehouse(source.value(), worker);
        api.assignUserToWarehouse(destination.value(), worker);
        bundle.stockPositions()
                .create(
                        StockPosition.of(
                                source,
                                sourceCell,
                                materialA,
                                StockState.AVAILABLE,
                                StockQuantity.of(new BigDecimal("100"))));
    }

    @Test
    void checkedRejectPreservesWaitingWithoutWaiver() {
        AcceptProductionDemandResult accepted = accept(line("10"));
        UUID documentId = sendPrepared(accepted);
        TransferDocumentRejectResult rejected =
                api.rejectTransferDocument(
                        new RejectTransferDocumentCommand(documentId, 0L, "brak", true));
        assertTrue(rejected.resupplyRequired());
        assertEquals(0, waiverCount());
        WarehouseDemandLineView line = demandQuery.getDemand(accepted.demandId()).orElseThrow().lines().getFirst();
        assertEquals(WarehouseDemandDerivedStatus.WAITING_FOR_SUPPLY, line.derivedStatus());
        assertEquals(0, line.waivedQuantity().compareTo(BigDecimal.ZERO));
    }

    @Test
    void uncheckedRejectWaivesAndMarksResupplyWaived() {
        AcceptProductionDemandResult accepted = accept(line("10"));
        UUID documentId = sendPrepared(accepted);
        TransferDocumentRejectResult rejected =
                api.rejectTransferDocument(
                        new RejectTransferDocumentCommand(documentId, 0L, "brak", false));
        assertFalse(rejected.resupplyRequired());
        assertEquals(1, waiverCount());
        WarehouseDemandLineView line = demandQuery.getDemand(accepted.demandId()).orElseThrow().lines().getFirst();
        assertEquals(WarehouseDemandDerivedStatus.RESUPPLY_WAIVED, line.derivedStatus());
        assertEquals(0, line.waivedQuantity().compareTo(new BigDecimal("10")));
        assertEquals(0, line.remainingQuantity().compareTo(BigDecimal.ZERO));
        assertFalse(demandQuery.getDemandSupplyTask(accepted.demandId()).isPresent());
    }

    @Test
    void returnAfterUncheckedRejectSettles() {
        AcceptProductionDemandResult accepted = accept(line("5"));
        UUID documentId = sendPrepared(accepted);
        api.rejectTransferDocument(
                new RejectTransferDocumentCommand(documentId, 0L, "reject", false));
        api.returnTransferMaterials(new ReturnTransferMaterialsCommand(documentId, 1L, List.of()));
        assertEquals(
                TransferSettlementState.SETTLED.name(),
                jdbc.queryForObject(
                        "SELECT settlement_state FROM warehouse.transfer_document_settlement WHERE document_id = ?",
                        String.class,
                        documentId));
        assertEquals(1, waiverCount());
    }

    @Test
    void doubleRejectOptimisticLock() {
        UUID documentId = sendPrepared(accept(line("10")));
        api.rejectTransferDocument(
                new RejectTransferDocumentCommand(documentId, 0L, "first", false));
        assertThrows(
                RuntimeException.class,
                () ->
                        api.rejectTransferDocument(
                                new RejectTransferDocumentCommand(documentId, 0L, "second", false)));
        assertEquals(1, waiverCount());
    }

    @Test
    void defaultRejectKeepsResupplyTrue() {
        UUID documentId = sendPrepared(accept(line("3")));
        TransferDocumentRejectResult rejected =
                api.rejectTransferDocument(
                        new RejectTransferDocumentCommand(documentId, 0L, "legacy"));
        assertTrue(rejected.resupplyRequired());
        assertEquals(0, waiverCount());
    }

    @Test
    void rejectRequiresTransferPermission() {
        UUID documentId = sendPrepared(accept(line("3")));
        permissions.set(Set.of(WarehousePermissions.WAREHOUSE_VIEW));
        assertThrows(
                AccessDeniedException.class,
                () ->
                        api.rejectTransferDocument(
                                new RejectTransferDocumentCommand(documentId, 0L, "x", false)));
    }

    @Test
    void v57SchemaPresent() {
        assertEquals(
                1,
                jdbc.queryForObject(
                                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='warehouse' AND table_name='warehouse_demand_resupply_waivers'",
                                Integer.class)
                        .intValue());
        Integer version =
                jdbc.queryForObject(
                        "SELECT MAX(CAST(version AS INT)) FROM flyway_schema_history WHERE success",
                        Integer.class);
        assertTrue(version != null && version >= 57);
    }

    private AcceptProductionDemandResult accept(ProductionDemandLine line) {
        return demandApi.acceptProductionDemand(
                new AcceptProductionDemandCommand(
                        UUID.randomUUID(), destination.value(), "system", List.of(line)));
    }

    private ProductionDemandLine line(String qty) {
        return new ProductionDemandLine(
                UUID.randomUUID(),
                materialA.article(),
                materialA.name(),
                materialA.color(),
                materialA.unitOfMeasure(),
                null,
                new BigDecimal(qty),
                materialA.id().value());
    }

    private UUID sendPrepared(AcceptProductionDemandResult accepted) {
        UUID documentId;
        if (accepted.documents().isEmpty()) {
            documentId =
                    demandApi
                            .prepareProductionDemandTransfers(accepted.demandId())
                            .documents()
                            .getFirst()
                            .documentId();
        } else {
            documentId = accepted.documents().getFirst().documentId();
        }
        TransferDocumentView loaded = api.getTransferDocument(documentId);
        if (DocumentStatus.DRAFT.name().equals(loaded.documentStatus())) {
            api.takeTransferTaskInWork(documentId);
            loaded = api.getTransferDocument(documentId);
            api.sendTransferDocument(
                    new SendTransferDocumentCommand(
                            documentId,
                            bundle.documentEngine().findById(documentId).orElseThrow().version(),
                            loaded.payloadRevision(),
                            List.of(
                                    new TransferDocumentSourceAllocationInput(
                                            loaded.lines().getFirst().lineId(),
                                            sourceCell.value(),
                                            loaded.lines().getFirst().quantity()))));
        }
        return documentId;
    }

    private int waiverCount() {
        Integer n =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.warehouse_demand_resupply_waivers",
                        Integer.class);
        return n == null ? 0 : n;
    }

    private SessionSummary sessionFor(UUID userId) {
        return new SessionSummary(
                SessionId.of(UUID.randomUUID()),
                UserId.of(userId),
                Login.of("user-" + userId.toString().substring(0, 8)),
                Instant.parse("2026-11-02T10:00:00Z"));
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

    private AuthorizationService authorizationFromPermissions() {
        return new AuthorizationService() {
            @Override
            public boolean hasPermission(PermissionId permissionId) {
                return permissions.get().contains(permissionId);
            }

            @Override
            public void requirePermission(PermissionId permissionId) {
                if (!hasPermission(permissionId)) {
                    throw new AccessDeniedException("Access denied: " + permissionId.value());
                }
            }

            @Override
            public Set<PermissionId> effectivePermissions() {
                return permissions.get();
            }
        };
    }
}