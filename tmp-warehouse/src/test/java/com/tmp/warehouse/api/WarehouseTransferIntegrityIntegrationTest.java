package com.tmp.warehouse.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.warehouse.api.WarehouseApi.OperationKind;
import com.tmp.warehouse.domain.InvalidWarehouseStateException;
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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** STAGE7-000C: exactly-once transfer receive, positive quantity, logical transfer status. */
@Testcontainers
class WarehouseTransferIntegrityIntegrationTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-18T06:00:00Z"), ZoneOffset.UTC);

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
        jdbc = bundle.jdbc();
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
    void caseA_oneTimeReceiveMovesStockOnce() {
        MaterialReference material = materialWithAvailable(20L);
        WarehouseApi.TransferRequestView draft = draft(material, 8);
        assertEquals("DRAFT", queryApi.getTransferStatus(draft.operationId()).status());

        WarehouseApi.OperationResult sent = commandApi.sendTransfer(draft.operationId());
        WarehouseApi.TransferStatusView afterSend = queryApi.getTransferStatus(draft.operationId());
        assertEquals("SENT", afterSend.status());
        assertNull(afterSend.receiveOperationId());
        assertEquals(0, quantity(mainWarehouseId, mainCellId, material, "AVAILABLE").compareTo(BigDecimal.valueOf(12)));
        assertEquals(0, quantity(mainWarehouseId, mainCellId, material, "IN_TRANSIT").compareTo(BigDecimal.valueOf(8)));

        WarehouseApi.OperationResult received = commandApi.receiveTransfer(sent.operationId());
        assertEquals(OperationKind.TRANSFER_RECEIVE, received.kind());
        WarehouseApi.TransferStatusView afterReceive =
                queryApi.getTransferStatus(draft.operationId());
        assertEquals("RECEIVED", afterReceive.status());
        assertEquals(received.operationId(), afterReceive.receiveOperationId());
        assertEquals(0, quantity(mainWarehouseId, mainCellId, material, "IN_TRANSIT").compareTo(BigDecimal.ZERO));
        assertEquals(
                0,
                quantity(productionWarehouseId, productionCellId, material, "AVAILABLE")
                        .compareTo(BigDecimal.valueOf(8)));
        assertEquals(4, movementCount());
    }

    @Test
    void caseB_sequentialDuplicateReceiveIsRejectedWithoutStockOrMovementChange() {
        MaterialReference material = materialWithAvailable(20L);
        WarehouseApi.TransferRequestView draft = draft(material, 8);
        WarehouseApi.OperationResult sent = commandApi.sendTransfer(draft.operationId());
        commandApi.receiveTransfer(sent.operationId());

        BigDecimal destAfterFirst =
                quantity(productionWarehouseId, productionCellId, material, "AVAILABLE");
        BigDecimal inTransitAfterFirst =
                quantity(mainWarehouseId, mainCellId, material, "IN_TRANSIT");
        int movementsAfterFirst = movementCount();

        assertThrows(
                InvalidWarehouseStateException.class,
                () -> commandApi.receiveTransfer(sent.operationId()));

        assertEquals(
                0,
                quantity(productionWarehouseId, productionCellId, material, "AVAILABLE")
                        .compareTo(destAfterFirst));
        assertEquals(
                0,
                quantity(mainWarehouseId, mainCellId, material, "IN_TRANSIT")
                        .compareTo(inTransitAfterFirst));
        assertEquals(movementsAfterFirst, movementCount());
        assertEquals("RECEIVED", queryApi.getTransferStatus(draft.operationId()).status());
    }

    @Test
    void caseC_duplicateReceiveADoesNotConsumeInTransitOfTransferB() {
        MaterialReference material = materialWithAvailable(20L);
        WarehouseApi.TransferRequestView draftA = draft(material, 8);
        WarehouseApi.TransferRequestView draftB = draft(material, 8);
        WarehouseApi.OperationResult sentA = commandApi.sendTransfer(draftA.operationId());
        WarehouseApi.OperationResult sentB = commandApi.sendTransfer(draftB.operationId());

        assertEquals(0, quantity(mainWarehouseId, mainCellId, material, "IN_TRANSIT").compareTo(BigDecimal.valueOf(16)));

        commandApi.receiveTransfer(sentA.operationId());
        assertEquals(0, quantity(mainWarehouseId, mainCellId, material, "IN_TRANSIT").compareTo(BigDecimal.valueOf(8)));
        assertEquals(
                0,
                quantity(productionWarehouseId, productionCellId, material, "AVAILABLE")
                        .compareTo(BigDecimal.valueOf(8)));

        int movementsAfterA = movementCount();
        assertThrows(
                InvalidWarehouseStateException.class,
                () -> commandApi.receiveTransfer(sentA.operationId()));
        assertEquals(0, quantity(mainWarehouseId, mainCellId, material, "IN_TRANSIT").compareTo(BigDecimal.valueOf(8)));
        assertEquals(movementsAfterA, movementCount());

        commandApi.receiveTransfer(sentB.operationId());
        assertEquals(0, quantity(mainWarehouseId, mainCellId, material, "IN_TRANSIT").compareTo(BigDecimal.ZERO));
        assertEquals(
                0,
                quantity(productionWarehouseId, productionCellId, material, "AVAILABLE")
                        .compareTo(BigDecimal.valueOf(16)));
        assertEquals("RECEIVED", queryApi.getTransferStatus(draftA.operationId()).status());
        assertEquals("RECEIVED", queryApi.getTransferStatus(draftB.operationId()).status());
    }

    @Test
    void caseD_concurrentDuplicateReceiveAllowsExactlyOneSuccess() throws Exception {
        MaterialReference material = materialWithAvailable(20L);
        WarehouseApi.TransferRequestView draft = draft(material, 8);
        UUID sendId = commandApi.sendTransfer(draft.operationId()).operationId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        try {
            Future<?> first = executor.submit(() -> receiveConcurrently(sendId, start, success, rejected));
            Future<?> second = executor.submit(() -> receiveConcurrently(sendId, start, success, rejected));
            start.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, success.get(), "exactly one concurrent receive must succeed");
        assertEquals(1, rejected.get(), "the other concurrent receive must be rejected");
        assertEquals(
                0,
                quantity(productionWarehouseId, productionCellId, material, "AVAILABLE")
                        .compareTo(BigDecimal.valueOf(8)));
        assertEquals("RECEIVED", queryApi.getTransferStatus(draft.operationId()).status());
        assertNotNull(queryApi.getTransferStatus(draft.operationId()).receiveOperationId());
    }

    @Test
    void databaseUniqueIndexRejectsTwoSendsSharingReceiveOperation() {
        MaterialReference material = materialWithAvailable(20L);
        WarehouseApi.TransferRequestView draftA = draft(material, 8);
        WarehouseApi.TransferRequestView draftB = draft(material, 8);
        UUID sendA = commandApi.sendTransfer(draftA.operationId()).operationId();
        UUID sendB = commandApi.sendTransfer(draftB.operationId()).operationId();
        UUID receiveA = commandApi.receiveTransfer(sendA).operationId();

        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                """
                                UPDATE warehouse.transfer_operation_context
                                SET receive_operation_id = ?
                                WHERE operation_id = ?
                                """,
                                receiveA,
                                sendB));
    }

    @Test
    void failedReceiveDoesNotLeaveReceivedMarkerAndCanBeRetried() {
        MaterialReference material = materialWithAvailable(20L);
        WarehouseApi.TransferRequestView draft = draft(material, 8);
        UUID sendId = commandApi.sendTransfer(draft.operationId()).operationId();

        TransactionTemplate outer = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        RuntimeException failure =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                outer.executeWithoutResult(
                                        status -> {
                                            commandApi.receiveTransfer(sendId);
                                            throw new RuntimeException("rollback probe");
                                        }));
        assertEquals("rollback probe", failure.getMessage());

        assertEquals("SENT", queryApi.getTransferStatus(draft.operationId()).status());
        assertNull(queryApi.getTransferStatus(draft.operationId()).receiveOperationId());
        assertEquals(0, quantity(mainWarehouseId, mainCellId, material, "IN_TRANSIT").compareTo(BigDecimal.valueOf(8)));
        assertEquals(
                0,
                quantity(productionWarehouseId, productionCellId, material, "AVAILABLE")
                        .compareTo(BigDecimal.ZERO));

        commandApi.receiveTransfer(sendId);
        assertEquals("RECEIVED", queryApi.getTransferStatus(draft.operationId()).status());
        assertEquals(
                0,
                quantity(productionWarehouseId, productionCellId, material, "AVAILABLE")
                        .compareTo(BigDecimal.valueOf(8)));
    }

    @Test
    void zeroQuantityDraftIsRejectedWithoutPersistence() {
        MaterialReference material = materialWithAvailable(20L);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        commandApi.createTransferDraft(
                                new WarehouseApi.CreateTransferDraftCommand(
                                        material.id().value(),
                                        BigDecimal.ZERO,
                                        mainWarehouseId.value(),
                                        mainCellId.value(),
                                        productionWarehouseId.value(),
                                        productionCellId.value())));

        assertEquals(0, operationCount());
        assertEquals(0, contextCount());
        assertEquals(0, movementCount());
        assertEquals(0, quantity(mainWarehouseId, mainCellId, material, "AVAILABLE").compareTo(BigDecimal.valueOf(20)));
    }

    @Test
    void positiveQuantityDraftSendReceiveHappyPath() {
        MaterialReference material = materialWithAvailable(20L);
        WarehouseApi.TransferRequestView draft = draft(material, 5);
        commandApi.sendTransfer(draft.operationId());
        commandApi.receiveTransfer(draft.operationId());
        assertEquals("RECEIVED", queryApi.getTransferStatus(draft.operationId()).status());
        assertEquals(
                0,
                quantity(productionWarehouseId, productionCellId, material, "AVAILABLE")
                        .compareTo(BigDecimal.valueOf(5)));
    }

    private MaterialReference materialWithAvailable(long available) {
        MaterialReference material =
                bundle.materials()
                        .create(MaterialReference.create("TI-1", "Transfer integrity", "", "", "шт."));
        bundle.stockPositions()
                .create(
                        StockPosition.of(
                                mainWarehouseId,
                                mainCellId,
                                material,
                                StockState.AVAILABLE,
                                StockQuantity.of(available)));
        return material;
    }

    private WarehouseApi.TransferRequestView draft(MaterialReference material, long quantity) {
        return commandApi.createTransferDraft(
                new WarehouseApi.CreateTransferDraftCommand(
                        material.id().value(),
                        BigDecimal.valueOf(quantity),
                        mainWarehouseId.value(),
                        mainCellId.value(),
                        productionWarehouseId.value(),
                        productionCellId.value()));
    }

    private void receiveConcurrently(
            UUID sendId, CountDownLatch start, AtomicInteger success, AtomicInteger rejected) {
        try {
            start.await(10, TimeUnit.SECONDS);
            commandApi.receiveTransfer(sendId);
            success.incrementAndGet();
        } catch (RuntimeException ex) {
            if (isAlreadyReceived(ex)) {
                rejected.incrementAndGet();
                return;
            }
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static boolean isAlreadyReceived(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof InvalidWarehouseStateException
                    && current.getMessage() != null
                    && current.getMessage().contains("already received")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private BigDecimal quantity(
            WarehouseId warehouseId, StorageCellId cellId, MaterialReference material, String state) {
        BigDecimal value =
                jdbc.query(
                        """
                        SELECT quantity FROM warehouse.stock_positions
                        WHERE warehouse_id = ?
                          AND storage_cell_id = ?
                          AND material_reference_id = ?
                          AND stock_state = ?
                        """,
                        rs -> rs.next() ? rs.getBigDecimal("quantity") : BigDecimal.ZERO,
                        warehouseId.value(),
                        cellId.value(),
                        material.id().value(),
                        state);
        return value == null ? BigDecimal.ZERO : value;
    }

    private int movementCount() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM warehouse.warehouse_movements", Integer.class);
        return count == null ? 0 : count;
    }

    private int operationCount() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM warehouse.warehouse_operations", Integer.class);
        return count == null ? 0 : count;
    }

    private int contextCount() {
        Integer count =
                jdbc.queryForObject("SELECT COUNT(*) FROM warehouse.transfer_operation_context", Integer.class);
        return count == null ? 0 : count;
    }
}
