package com.tmp.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.tmp.warehouse.api.WarehouseApi.CreateTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.SendTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentLineInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentSourceAllocationInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskKind;
import com.tmp.warehouse.api.WarehouseDemandCommandApi;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.AcceptProductionDemandCommand;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.AcceptProductionDemandResult;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.DemandLineRoutingOutcome;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.ProductionDemandLine;
import com.tmp.warehouse.application.document.WarehouseTransferDocumentProcessor;
import com.tmp.warehouse.domain.InvalidWarehouseStateException;
import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockPosition;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.StorageCell;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseTransferDocument;
import com.tmp.warehouse.persistence.JdbcAvailableStockAggregationQuery;
import com.tmp.warehouse.persistence.JdbcWarehouseDemandRepository;
import com.tmp.warehouse.security.WarehousePermissions;
import com.tmp.warehouse.testsupport.WarehouseIntegrationTestSupport;
import com.tmp.warehouse.testsupport.WarehouseJdbcTestSupport;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
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
 * Warehouse-owned Demand command — accept Production Demand, best-effort Transfer DRAFTs, no stock
 * mutation, no responsibility guard.
 */
@Testcontainers
class WarehouseDemandCommandApiIntegrationTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-10T08:00:00Z"), ZoneOffset.UTC);

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
    private UUID worker;
    private UUID stranger;
    private WarehouseId destination;
    private WarehouseId sourceA;
    private WarehouseId sourceB;
    private StorageCellId cellA;
    private StorageCellId cellB;
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
        stranger = UUID.randomUUID();
        session.set(sessionFor(stranger));
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
        demandApi =
                new DefaultWarehouseDemandCommandApi(
                        new MaterialSourceRoutingService(
                                new JdbcAvailableStockAggregationQuery(jdbc)),
                        bundle.transferDocumentService(),
                        bundle.catalog(),
                        bundle.materials(),
                        new JdbcWarehouseDemandRepository(jdbc),
                        bundle.transferDocuments(),
                        new com.tmp.warehouse.persistence.JdbcWarehouseDemandFulfillmentReadQuery(
                                jdbc),
                        CLOCK,
                        new TransactionTemplate(new DataSourceTransactionManager(dataSource)));

        destination = WarehouseId.generate();
        sourceA = WarehouseId.generate();
        sourceB = WarehouseId.generate();
        bundle.catalog().save(Warehouse.create(destination, "DEST", "Destination"));
        bundle.catalog().save(Warehouse.create(sourceA, "SRC-A", "Source A"));
        bundle.catalog().save(Warehouse.create(sourceB, "SRC-B", "Source B"));
        cellA = StorageCellId.generate();
        cellB = StorageCellId.generate();
        bundle.catalog().save(StorageCell.create(cellA, sourceA, "A-1"));
        bundle.catalog().save(StorageCell.create(cellB, sourceB, "B-1"));
        materialA =
                WarehouseJdbcTestSupport.persistMaterial(
                        jdbc,
                        CLOCK,
                        MaterialReference.create("ART-A", "ART-A", "", "", "шт."));
        materialB =
                WarehouseJdbcTestSupport.persistMaterial(
                        jdbc,
                        CLOCK,
                        MaterialReference.create("ART-B", "ART-B", "", "", "шт."));
    }

    @Test
    void oneDemandFullAvailableCreatesSingleDraftWithoutStockMutation() {
        seedAvailable(sourceA, cellA, materialA, "100");
        int stock = count("warehouse.stock_positions");
        int ops = count("warehouse.warehouse_operations");
        int moves = count("warehouse.warehouse_movements");
        UUID sourceLineId = UUID.randomUUID();

        AcceptProductionDemandResult result =
                demandApi.acceptProductionDemand(
                        command(line(sourceLineId, materialA, "40")));

        assertTrue(result.created());
        assertEquals(1, result.documents().size());
        assertEquals(sourceA.value(), result.documents().getFirst().sourceWarehouseId());
        assertEquals(destination.value(), result.documents().getFirst().destinationWarehouseId());
        assertEquals(1, result.lineOutcomes().size());
        assertEquals(DemandLineRoutingOutcome.ROUTED, result.lineOutcomes().getFirst().outcome());
        assertEquals(
                0,
                new BigDecimal("40")
                        .compareTo(documentLineQuantity(result.documents().getFirst().documentId())));
        assertEquals(
                DocumentStatus.DRAFT,
                bundle.documentEngine()
                        .findById(result.documents().getFirst().documentId())
                        .orElseThrow()
                        .status());
        assertEquals(stock, count("warehouse.stock_positions"));
        assertEquals(ops, count("warehouse.warehouse_operations"));
        assertEquals(moves, count("warehouse.warehouse_movements"));
        assertEquals(
                0,
                jdbc.queryForObject(
                                "SELECT COALESCE(SUM(quantity),0) FROM warehouse.stock_positions",
                                BigDecimal.class)
                        .compareTo(new BigDecimal("100")));
    }

    @Test
    void destinationStockIsExcludedAndHighestFullSourceSelected() {
        StorageCellId destCell = StorageCellId.generate();
        bundle.catalog().save(StorageCell.create(destCell, destination, "D-1"));
        seedAvailable(destination, destCell, materialA, "999");
        seedAvailable(sourceA, cellA, materialA, "50");
        seedAvailable(sourceB, cellB, materialA, "80");

        AcceptProductionDemandResult result =
                demandApi.acceptProductionDemand(
                        command(line(UUID.randomUUID(), materialA, "60")));

        assertEquals(1, result.documents().size());
        assertEquals(sourceB.value(), result.documents().getFirst().sourceWarehouseId());
    }

    @Test
    void partialPositiveSourceStillCreatesFullDemandQuantityDocument() {
        seedAvailable(sourceA, cellA, materialA, "60");

        AcceptProductionDemandResult result =
                demandApi.acceptProductionDemand(
                        command(line(UUID.randomUUID(), materialA, "100")));

        assertEquals(1, result.documents().size());
        assertEquals(DemandLineRoutingOutcome.ROUTED, result.lineOutcomes().getFirst().outcome());
        assertEquals(
                0,
                new BigDecimal("100")
                        .compareTo(documentLineQuantity(result.documents().getFirst().documentId())));
    }

    @Test
    void noAvailableSourcePersistsDemandWithWaitingReasonAndNoDocuments() {
        seedAvailable(sourceA, cellA, materialA, "10");

        AcceptProductionDemandResult result =
                demandApi.acceptProductionDemand(
                        command(
                                line(UUID.randomUUID(), materialA, "5"),
                                line(UUID.randomUUID(), materialB, "5")));

        assertTrue(result.created());
        assertEquals(1, result.documents().size());
        assertEquals(2, result.lineOutcomes().size());
        assertEquals(
                1,
                result.lineOutcomes().stream()
                        .filter(o -> o.outcome() == DemandLineRoutingOutcome.ROUTED)
                        .count());
        assertEquals(
                1,
                result.lineOutcomes().stream()
                        .filter(o -> o.outcome() == DemandLineRoutingOutcome.NO_AVAILABLE_STOCK)
                        .count());
        assertEquals(1, count("warehouse.transfer_document_payload"));
        assertEquals(1, count("warehouse.warehouse_demands"));
    }

    @Test
    void sameSourceLinesShareOneDocumentWithDeterministicOrder() {
        seedAvailable(sourceA, cellA, materialA, "50");
        seedAvailable(sourceA, cellA, materialB, "50");

        AcceptProductionDemandResult result =
                demandApi.acceptProductionDemand(
                        command(
                                line(UUID.randomUUID(), materialA, "10"),
                                line(UUID.randomUUID(), materialB, "20")));

        assertEquals(1, result.documents().size());
        WarehouseTransferDocument payload =
                bundle.transferDocuments()
                        .findByDocumentId(result.documents().getFirst().documentId())
                        .orElseThrow();
        assertEquals(2, payload.lines().size());
        assertEquals(materialA.id(), payload.lines().get(0).materialReferenceId());
        assertEquals(materialB.id(), payload.lines().get(1).materialReferenceId());
        assertEquals(0, new BigDecimal("10").compareTo(payload.lines().get(0).quantity().value()));
        assertEquals(0, new BigDecimal("20").compareTo(payload.lines().get(1).quantity().value()));
    }

    @Test
    void differentSourcesCreateOneDocumentEach() {
        seedAvailable(sourceA, cellA, materialA, "50");
        seedAvailable(sourceB, cellB, materialB, "50");

        AcceptProductionDemandResult result =
                demandApi.acceptProductionDemand(
                        command(
                                line(UUID.randomUUID(), materialA, "10"),
                                line(UUID.randomUUID(), materialB, "20")));

        assertEquals(2, result.documents().size());
        assertEquals(sourceA.value(), result.documents().get(0).sourceWarehouseId());
        assertEquals(sourceB.value(), result.documents().get(1).sourceWarehouseId());
    }

    @Test
    void demandCreationDoesNotRequireSourceResponsibilityWhileManualCreateDoes() {
        seedAvailable(sourceA, cellA, materialA, "50");
        session.set(sessionFor(stranger));

        AcceptProductionDemandResult result =
                demandApi.acceptProductionDemand(
                        command(line(UUID.randomUUID(), materialA, "10")));
        assertEquals(1, result.documents().size());

        assertThrows(
                AccessDeniedException.class,
                () ->
                        api.createTransferDocument(
                                new CreateTransferDocumentCommand(
                                        sourceA.value(),
                                        destination.value(),
                                        List.of(
                                                new TransferDocumentLineInput(
                                                        null,
                                                        materialA.id().value(),
                                                        new BigDecimal("10"),
                                                        1)))));
    }

    @Test
    void generatedDraftAppearsInOperationalInboxForSourceResponsibleUser() {
        seedAvailable(sourceA, cellA, materialA, "50");
        AcceptProductionDemandResult result =
                demandApi.acceptProductionDemand(
                        command(line(UUID.randomUUID(), materialA, "10")));

        api.assignUserToWarehouse(sourceA.value(), worker);
        session.set(sessionFor(worker));
        var tasks = api.listMyWarehouseTasks(null);
        assertEquals(1, tasks.size());
        assertEquals(result.documents().getFirst().documentId(), tasks.getFirst().documentId());
        assertEquals(WarehouseTaskKind.TRANSFER_PREPARATION, tasks.getFirst().taskKind());
    }

    @Test
    void inactiveDestinationIsRejected() {
        WarehouseId inactive = WarehouseId.generate();
        bundle.catalog().save(Warehouse.of(inactive, "INACT", "Inactive", false));
        seedAvailable(sourceA, cellA, materialA, "50");
        assertThrows(
                InvalidWarehouseStateException.class,
                () ->
                        demandApi.acceptProductionDemand(
                                new AcceptProductionDemandCommand(
                                        UUID.randomUUID(),
                                        inactive.value(),
                                        "system",
                                        List.of(line(UUID.randomUUID(), materialA, "10")))));
        assertEquals(0, count("warehouse.transfer_document_payload"));
        assertEquals(0, count("warehouse.warehouse_demands"));
    }

    @Test
    void unmatchedMaterialSucceedsWithWaitingReason() {
        seedAvailable(sourceA, cellA, materialA, "50");
        UUID sourceLineId = UUID.randomUUID();

        AcceptProductionDemandResult result =
                demandApi.acceptProductionDemand(
                        command(
                                new ProductionDemandLine(
                                        sourceLineId,
                                        "UNKNOWN-ART",
                                        "Unknown",
                                        "",
                                        "шт.",
                                        null,
                                        new BigDecimal("10"),
                                        null)));

        assertTrue(result.created());
        assertTrue(result.documents().isEmpty());
        assertEquals(1, result.lineOutcomes().size());
        assertEquals(
                DemandLineRoutingOutcome.MATERIAL_UNMATCHED,
                result.lineOutcomes().getFirst().outcome());
        assertEquals(1, count("warehouse.warehouse_demands"));
        assertEquals(0, count("warehouse.transfer_document_payload"));
    }

    @Test
    void stage357PartialSendCreatesShortfallContinuationWithoutExtraDemandDocs() {
        seedAvailable(sourceA, cellA, materialA, "60");
        AcceptProductionDemandResult result =
                demandApi.acceptProductionDemand(
                        command(line(UUID.randomUUID(), materialA, "100")));
        UUID documentId = result.documents().getFirst().documentId();
        assertEquals(0, new BigDecimal("100").compareTo(documentLineQuantity(documentId)));

        api.assignUserToWarehouse(sourceA.value(), worker);
        session.set(sessionFor(worker));
        api.takeTransferTaskInWork(documentId);
        TransferDocumentView loaded = api.getTransferDocument(documentId);
        var sent =
                api.sendTransferDocument(
                        new SendTransferDocumentCommand(
                                documentId,
                                bundle.documentEngine().findById(documentId).orElseThrow().version(),
                                loaded.payloadRevision(),
                                List.of(
                                        new TransferDocumentSourceAllocationInput(
                                                loaded.lines().getFirst().lineId(),
                                                cellA.value(),
                                                new BigDecimal("60")))));

        assertEquals(DocumentStatus.POSTED.name(), sent.documentStatus());
        assertTrue(sent.continuationDocumentId() != null);
        TransferDocumentView continuation = api.getTransferDocument(sent.continuationDocumentId());
        assertEquals("SHORTFALL", continuation.continuationReason());
        assertEquals(0, new BigDecimal("40").compareTo(continuation.lines().getFirst().quantity()));
        assertEquals(2, count("warehouse.transfer_document_payload"));
    }

    @Test
    void missingDestinationIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        demandApi.acceptProductionDemand(
                                new AcceptProductionDemandCommand(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        "system",
                                        List.of(line(UUID.randomUUID(), materialA, "10")))));
    }

    private AcceptProductionDemandCommand command(ProductionDemandLine... lines) {
        return new AcceptProductionDemandCommand(
                UUID.randomUUID(), destination.value(), "system", Arrays.asList(lines));
    }

    private ProductionDemandLine line(UUID sourceLineId, MaterialReference material, String qty) {
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

    private void seedAvailable(
            WarehouseId warehouse, StorageCellId cell, MaterialReference material, String qty) {
        bundle.stockPositions()
                .create(
                        StockPosition.of(
                                warehouse,
                                cell,
                                material,
                                StockState.AVAILABLE,
                                StockQuantity.of(new BigDecimal(qty))));
    }

    private BigDecimal documentLineQuantity(UUID documentId) {
        return jdbc.queryForObject(
                """
                SELECT quantity FROM warehouse.transfer_document_lines
                WHERE document_id = ?
                ORDER BY line_order
                LIMIT 1
                """,
                BigDecimal.class,
                documentId);
    }

    private int count(String table) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return n == null ? 0 : n;
    }

    private void grantFullWarehousePermissions() {
        permissions.set(
                Set.of(
                        WarehousePermissions.WAREHOUSE_TRANSFER,
                        WarehousePermissions.WAREHOUSE_VIEW,
                        WarehousePermissions.WAREHOUSE_STRUCTURE_CREATE,
                        WarehousePermissions.WAREHOUSE_STRUCTURE_UPDATE,
                        WarehousePermissions.WAREHOUSE_STRUCTURE_VIEW,
                        WarehousePermissions.STORAGE_CELL_CREATE));
    }

    private SessionSummary sessionFor(UUID userId) {
        return new SessionSummary(
                SessionId.of(UUID.randomUUID()),
                UserId.of(userId),
                Login.of("user-" + userId.toString().substring(0, 8)),
                Instant.parse("2026-09-10T08:00:00Z"));
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
