package com.tmp.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.tmp.warehouse.api.WarehouseApi.ConsumptionCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateStorageCellCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateWarehouseCommand;
import com.tmp.warehouse.api.WarehouseApi.ExecuteOperationCommand;
import com.tmp.warehouse.api.WarehouseApi.ReceiptCommand;
import com.tmp.warehouse.api.WarehouseApi.ReceiveTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.RejectTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.ReturnTransferMaterialsCommand;
import com.tmp.warehouse.api.WarehouseApi.SendTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentDestinationAllocationInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentLineInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentSourceAllocationInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseHistoryEntryView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseHistoryFilter;
import com.tmp.warehouse.api.WarehouseApi.WarehouseHistoryPage;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.TransferSettlementState;
import com.tmp.warehouse.testsupport.WarehouseIntegrationTestSupport;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Stage 3.5.12: Warehouse History read model — physical movements, filters, security. */
@Testcontainers
class WarehouseHistoryIntegrationTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-11T10:00:00Z"), ZoneOffset.UTC);
    private static final UUID USER = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant TO_EXCLUSIVE = Instant.parse("2026-09-12T00:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    private WarehouseIntegrationTestSupport.ApiBundle bundle;

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
        jdbc.update("DELETE FROM warehouse.material_reservation_links");
        jdbc.update("DELETE FROM warehouse.warehouse_movements");
        jdbc.update("DELETE FROM warehouse.warehouse_operations");
        jdbc.update("DELETE FROM warehouse.stock_positions");
        jdbc.update("DELETE FROM warehouse.storage_cells");
        jdbc.update("DELETE FROM warehouse.warehouses");
        jdbc.update("DELETE FROM warehouse.material_references");

        bundle =
                WarehouseIntegrationTestSupport.createApiBundle(
                        dataSource,
                        CLOCK,
                        allowAllAuth(),
                        fixedUser(USER),
                        WarehouseIntegrationTestSupport.permitAllResponsibility());
    }

    @Test
    void receiptMoveConsumptionAdjustmentAppearWithPhysicalQuantities() {
        UUID warehouseId = createWarehouse("WH-H1", "History One");
        assign(warehouseId);
        UUID cell1 = createCell(warehouseId, "1-01");
        UUID cell2 = createCell(warehouseId, "1-05");

        bundle.api()
                .receive(
                        new ReceiptCommand(
                                "A100",
                                "Профиль",
                                "",
                                "",
                                "м",
                                new BigDecimal("50"),
                                warehouseId,
                                cell1));
        UUID materialId = materialId("A100");

        bundle.api()
                .executeWarehouseOperation(
                        ExecuteOperationCommand.move(
                                materialId,
                                new BigDecimal("20"),
                                warehouseId,
                                cell1,
                                warehouseId,
                                cell2));
        bundle.api()
                .consume(
                        new ConsumptionCommand(
                                materialId, new BigDecimal("10"), warehouseId, cell2));
        bundle.api()
                .executeWarehouseOperation(
                        ExecuteOperationCommand.adjustment(
                                materialId, new BigDecimal("-2"), warehouseId, cell2));

        Map<String, Object> before = snapshotFacts();
        WarehouseHistoryPage page = listAll(warehouseId);
        assertEquals(before, snapshotFacts());

        assertEquals(4, page.totalElements());
        WarehouseHistoryEntryView receipt = byType(page, "RECEIPT");
        WarehouseHistoryEntryView move = byType(page, "MOVE");
        WarehouseHistoryEntryView consumption = byType(page, "CONSUMPTION");
        WarehouseHistoryEntryView adjustment = byType(page, "ADJUSTMENT");
        assertEquals("Приход", receipt.operationDisplayName());
        assertEquals(0, new BigDecimal("50").compareTo(receipt.quantity()));
        assertEquals("Перемещение", move.operationDisplayName());
        assertEquals(0, new BigDecimal("20").compareTo(move.quantity()));
        assertEquals("1-01", move.sourceCellCode());
        assertEquals("1-05", move.destinationCellCode());
        assertEquals("Списание", consumption.operationDisplayName());
        assertEquals(0, new BigDecimal("-10").compareTo(consumption.quantity()));
        assertEquals("Корректировка", adjustment.operationDisplayName());
        assertEquals(0, new BigDecimal("-2").compareTo(adjustment.quantity()));

        // Deterministic newest-first when timestamps differ
        jdbc.update(
                "UPDATE warehouse.warehouse_operations SET updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse("2026-09-11T10:00:04Z")),
                adjustment.entryId());
        jdbc.update(
                "UPDATE warehouse.warehouse_operations SET updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse("2026-09-11T10:00:03Z")),
                consumption.entryId());
        jdbc.update(
                "UPDATE warehouse.warehouse_operations SET updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse("2026-09-11T10:00:02Z")),
                move.entryId());
        jdbc.update(
                "UPDATE warehouse.warehouse_operations SET updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse("2026-09-11T10:00:01Z")),
                receipt.entryId());
        WarehouseHistoryPage ordered = listAll(warehouseId);
        assertEquals("ADJUSTMENT", ordered.content().get(0).operationType());
        assertEquals("CONSUMPTION", ordered.content().get(1).operationType());
        assertEquals("MOVE", ordered.content().get(2).operationType());
        assertEquals("RECEIPT", ordered.content().get(3).operationType());
    }

    @Test
    void historyConservationForSimpleReceiptConsumePath() {
        UUID warehouseId = createWarehouse("WH-C", "Conserve");
        assign(warehouseId);
        UUID cell = createCell(warehouseId, "C-1");
        bundle.api()
                .receive(
                        new ReceiptCommand(
                                "C100",
                                "Conserve",
                                "",
                                "",
                                "шт",
                                new BigDecimal("40"),
                                warehouseId,
                                cell));
        UUID materialId = materialId("C100");
        BigDecimal opening = BigDecimal.ZERO;
        WarehouseHistoryPage afterReceipt = listAll(warehouseId);
        BigDecimal afterReceiptSum =
                afterReceipt.content().stream()
                        .map(WarehouseHistoryEntryView::quantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, opening.add(afterReceiptSum).compareTo(available(materialId, cell)));

        bundle.api()
                .consume(
                        new ConsumptionCommand(
                                materialId, new BigDecimal("15"), warehouseId, cell));
        WarehouseHistoryPage page = listAll(warehouseId);
        BigDecimal movementSum =
                page.content().stream()
                        .map(WarehouseHistoryEntryView::quantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, opening.add(movementSum).compareTo(available(materialId, cell)));
        assertEquals(0, new BigDecimal("25").compareTo(available(materialId, cell)));
    }

    @Test
    void transferSendReceiveReturnUsePhysicalQuantitiesAndRejectAddsNoMovement() {
        UUID sourceId = createWarehouse("WH-S", "Source");
        UUID destId = createWarehouse("WH-D", "Dest");
        assign(sourceId);
        assign(destId);
        UUID sourceCell = createCell(sourceId, "S-1");
        UUID destCell = createCell(destId, "D-1");

        bundle.api()
                .receive(
                        new ReceiptCommand(
                                "T100",
                                "TransferMat",
                                "",
                                "",
                                "м",
                                new BigDecimal("100"),
                                sourceId,
                                sourceCell));
        UUID materialId = materialId("T100");
        long opsBeforeDraft = countCompletedOps();
        long movesBeforeDraft = countMovements();

        TransferDocumentView draft =
                bundle.api()
                        .createTransferDocument(
                                new CreateTransferDocumentCommand(
                                        sourceId,
                                        destId,
                                        List.of(
                                                new TransferDocumentLineInput(
                                                        null,
                                                        materialId,
                                                        new BigDecimal("100"),
                                                        1))));
        assertEquals(opsBeforeDraft, countCompletedOps());
        assertEquals(movesBeforeDraft, countMovements());
        assertEquals(1, listAll(sourceId).totalElements());

        bundle.api()
                .sendTransferDocument(
                        new SendTransferDocumentCommand(
                                draft.documentId(),
                                documentVersion(draft.documentId()),
                                draft.payloadRevision(),
                                List.of(
                                        new TransferDocumentSourceAllocationInput(
                                                draft.lines().get(0).lineId(),
                                                sourceCell,
                                                new BigDecimal("98")))));
        TransferDocumentView sent = bundle.api().getTransferDocument(draft.documentId());

        WarehouseHistoryEntryView sendRow =
                listAll(sourceId).content().stream()
                        .filter(e -> "TRANSFER_SEND".equals(e.operationType()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(0, new BigDecimal("-98").compareTo(sendRow.quantity()));
        assertEquals(draft.documentId(), sendRow.documentId());
        assertTrue(sendRow.documentNumber() != null && !sendRow.documentNumber().isBlank());

        long opsAfterSend = countCompletedOps();
        long movesAfterSend = countMovements();
        bundle.api()
                .rejectTransferDocument(
                        new RejectTransferDocumentCommand(
                                sent.documentId(),
                                sent.operationalRevision() == null
                                        ? 0L
                                        : sent.operationalRevision(),
                                "брак"));
        assertEquals(opsAfterSend, countCompletedOps());
        assertEquals(movesAfterSend, countMovements());
        assertTrue(
                listAll(sourceId).content().stream()
                        .noneMatch(e -> "TRANSFER_RECEIVE".equals(e.operationType())));

        TransferDocumentView rejected = bundle.api().getTransferDocument(sent.documentId());
        assertEquals(TransferSettlementState.RETURN_PENDING.name(), rejected.settlementState());
        bundle.api()
                .returnTransferMaterials(
                        new ReturnTransferMaterialsCommand(
                                rejected.documentId(),
                                rejected.operationalRevision() == null
                                        ? 1L
                                        : rejected.operationalRevision(),
                                List.of()));
        WarehouseHistoryEntryView returnRow =
                listAll(sourceId).content().stream()
                        .filter(e -> "TRANSFER_RETURN".equals(e.operationType()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(0, new BigDecimal("98").compareTo(returnRow.quantity()));
        assertEquals("S-1", returnRow.destinationCellCode());

        // Partial receive on a second document
        bundle.api()
                .receive(
                        new ReceiptCommand(
                                "T200",
                                "Partial",
                                "",
                                "",
                                "м",
                                new BigDecimal("100"),
                                sourceId,
                                sourceCell));
        UUID material2 = materialId("T200");
        TransferDocumentView draft2 =
                bundle.api()
                        .createTransferDocument(
                                new CreateTransferDocumentCommand(
                                        sourceId,
                                        destId,
                                        List.of(
                                                new TransferDocumentLineInput(
                                                        null,
                                                        material2,
                                                        new BigDecimal("100"),
                                                        1))));
        bundle.api()
                .sendTransferDocument(
                        new SendTransferDocumentCommand(
                                draft2.documentId(),
                                documentVersion(draft2.documentId()),
                                draft2.payloadRevision(),
                                List.of(
                                        new TransferDocumentSourceAllocationInput(
                                                draft2.lines().get(0).lineId(),
                                                sourceCell,
                                                new BigDecimal("98")))));
        TransferDocumentView awaiting = bundle.api().getTransferDocument(draft2.documentId());
        bundle.api()
                .receiveTransferDocument(
                        new ReceiveTransferDocumentCommand(
                                awaiting.documentId(),
                                awaiting.operationalRevision() == null
                                        ? 0L
                                        : awaiting.operationalRevision(),
                                List.of(
                                        new TransferDocumentDestinationAllocationInput(
                                                awaiting.lines().get(0).lineId(),
                                                destCell,
                                                new BigDecimal("97")))));
        WarehouseHistoryEntryView receiveRow =
                listAll(destId).content().stream()
                        .filter(e -> "TRANSFER_RECEIVE".equals(e.operationType()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(0, new BigDecimal("97").compareTo(receiveRow.quantity()));

        TransferDocumentView partial = bundle.api().getTransferDocument(awaiting.documentId());
        assertEquals(TransferSettlementState.RETURN_PENDING.name(), partial.settlementState());
        bundle.api()
                .returnTransferMaterials(
                        new ReturnTransferMaterialsCommand(
                                partial.documentId(),
                                partial.operationalRevision() == null
                                        ? 1L
                                        : partial.operationalRevision(),
                                List.of()));
        WarehouseHistoryEntryView outstandingReturn =
                listAll(sourceId).content().stream()
                        .filter(e -> "TRANSFER_RETURN".equals(e.operationType()))
                        .filter(e -> material2.equals(e.materialReferenceId()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(0, new BigDecimal("1").compareTo(outstandingReturn.quantity()));
    }

    @Test
    void multiMaterialTransferDocumentProducesOneHistoryRowPerMaterial() {
        UUID sourceId = createWarehouse("WH-MM", "MultiMat");
        UUID destId = createWarehouse("WH-MM-D", "MultiMat Dest");
        assign(sourceId);
        assign(destId);
        UUID cell = createCell(sourceId, "MM-1");
        createCell(destId, "MM-D1");

        bundle.api()
                .receive(
                        new ReceiptCommand(
                                "A100", "Alpha", "", "", "м", new BigDecimal("20"), sourceId, cell));
        bundle.api()
                .receive(
                        new ReceiptCommand(
                                "B200", "Beta", "", "", "шт", new BigDecimal("10"), sourceId, cell));
        UUID aId = materialId("A100");
        UUID bId = materialId("B200");

        TransferDocumentView draft =
                bundle.api()
                        .createTransferDocument(
                                new CreateTransferDocumentCommand(
                                        sourceId,
                                        destId,
                                        List.of(
                                                new TransferDocumentLineInput(
                                                        null, aId, new BigDecimal("20"), 1),
                                                new TransferDocumentLineInput(
                                                        null, bId, new BigDecimal("10"), 2))));
        UUID lineA = draft.lines().get(0).lineId();
        UUID lineB = draft.lines().get(1).lineId();
        bundle.api()
                .sendTransferDocument(
                        new SendTransferDocumentCommand(
                                draft.documentId(),
                                documentVersion(draft.documentId()),
                                draft.payloadRevision(),
                                List.of(
                                        new TransferDocumentSourceAllocationInput(
                                                lineA, cell, new BigDecimal("20")),
                                        new TransferDocumentSourceAllocationInput(
                                                lineB, cell, new BigDecimal("10")))));

        List<WarehouseHistoryEntryView> sends =
                listAll(sourceId).content().stream()
                        .filter(e -> "TRANSFER_SEND".equals(e.operationType()))
                        .filter(e -> draft.documentId().equals(e.documentId()))
                        .toList();
        assertEquals(2, sends.size());
        assertEquals(
                1, sends.stream().filter(e -> aId.equals(e.materialReferenceId())).count());
        assertEquals(
                1, sends.stream().filter(e -> bId.equals(e.materialReferenceId())).count());
        assertEquals(
                0,
                sends.stream()
                        .filter(e -> aId.equals(e.materialReferenceId()))
                        .findFirst()
                        .orElseThrow()
                        .quantity()
                        .compareTo(new BigDecimal("-20")));
        assertEquals(
                0,
                sends.stream()
                        .filter(e -> bId.equals(e.materialReferenceId()))
                        .findFirst()
                        .orElseThrow()
                        .quantity()
                        .compareTo(new BigDecimal("-10")));
    }

    @Test
    void multiCellSameMaterialSendAggregatesToOnePrimaryHistoryEntry() {
        UUID sourceId = createWarehouse("WH-MC", "MultiCell");
        UUID destId = createWarehouse("WH-MC-D", "MultiCell Dest");
        assign(sourceId);
        assign(destId);
        UUID cell1 = createCell(sourceId, "1-01");
        UUID cell2 = createCell(sourceId, "1-02");
        UUID cell3 = createCell(sourceId, "1-03");
        createCell(destId, "D-1");

        bundle.api()
                .receive(
                        new ReceiptCommand(
                                "A100",
                                "Profile",
                                "",
                                "",
                                "м",
                                new BigDecimal("40"),
                                sourceId,
                                cell1));
        bundle.api()
                .receive(
                        new ReceiptCommand(
                                "A100",
                                "Profile",
                                "",
                                "",
                                "м",
                                new BigDecimal("30"),
                                sourceId,
                                cell2));
        bundle.api()
                .receive(
                        new ReceiptCommand(
                                "A100",
                                "Profile",
                                "",
                                "",
                                "м",
                                new BigDecimal("28"),
                                sourceId,
                                cell3));
        UUID materialId = materialId("A100");

        TransferDocumentView draft =
                bundle.api()
                        .createTransferDocument(
                                new CreateTransferDocumentCommand(
                                        sourceId,
                                        destId,
                                        List.of(
                                                new TransferDocumentLineInput(
                                                        null,
                                                        materialId,
                                                        new BigDecimal("98"),
                                                        1))));
        UUID lineId = draft.lines().get(0).lineId();
        bundle.api()
                .sendTransferDocument(
                        new SendTransferDocumentCommand(
                                draft.documentId(),
                                documentVersion(draft.documentId()),
                                draft.payloadRevision(),
                                List.of(
                                        new TransferDocumentSourceAllocationInput(
                                                lineId, cell1, new BigDecimal("40")),
                                        new TransferDocumentSourceAllocationInput(
                                                lineId, cell2, new BigDecimal("30")),
                                        new TransferDocumentSourceAllocationInput(
                                                lineId, cell3, new BigDecimal("28")))));

        assertEquals(
                3L,
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM warehouse.warehouse_operations
                         WHERE operation_type = 'TRANSFER_SEND' AND status = 'COMPLETED'
                           AND material_reference_id = ?
                        """,
                        Long.class,
                        materialId));

        List<WarehouseHistoryEntryView> sends =
                listAll(sourceId).content().stream()
                        .filter(e -> "TRANSFER_SEND".equals(e.operationType()))
                        .filter(e -> materialId.equals(e.materialReferenceId()))
                        .filter(e -> draft.documentId().equals(e.documentId()))
                        .toList();
        assertEquals(1, sends.size());
        WarehouseHistoryEntryView send = sends.get(0);
        assertEquals(0, new BigDecimal("-98").compareTo(send.quantity()));
        assertEquals(sourceId, send.sourceWarehouseId());
        assertEquals(destId, send.destinationWarehouseId());
        assertEquals(null, send.sourceCellCode());
        assertEquals(null, send.destinationCellCode());
    }

    @Test
    void filtersPaginationSortingSecurityAndEmpty() {
        UUID mine = createWarehouse("WH-M", "Mine");
        UUID foreign = createWarehouse("WH-F", "Foreign");
        assign(mine);
        UUID cell = createCell(mine, "M-1");
        createCell(foreign, "F-1");

        bundle.api()
                .receive(
                        new ReceiptCommand(
                                "A100", "Alpha", "", "", "м", new BigDecimal("5"), mine, cell));
        bundle.api()
                .receive(
                        new ReceiptCommand(
                                "B200", "Beta", "", "", "шт", new BigDecimal("7"), mine, cell));
        UUID aId = materialId("A100");
        bundle.api().consume(new ConsumptionCommand(aId, new BigDecimal("1"), mine, cell));

        assertThrows(AccessDeniedException.class, () -> listAll(foreign));

        WarehouseHistoryPage allMine = list(null, FROM, TO_EXCLUSIVE, null, null, 0, 50);
        assertTrue(allMine.totalElements() >= 3);

        assertEquals(2, list(mine, FROM, TO_EXCLUSIVE, "  a100  ", null, 0, 50).totalElements());
        assertEquals(1, list(mine, FROM, TO_EXCLUSIVE, "BETA", null, 0, 50).totalElements());
        assertEquals(
                1, list(mine, FROM, TO_EXCLUSIVE, null, "CONSUMPTION", 0, 50).totalElements());

        assertEquals(
                0,
                list(mine, FROM, Instant.parse("2026-09-10T00:00:00Z"), null, null, 0, 50)
                        .totalElements());
        assertEquals(
                0,
                list(
                                mine,
                                Instant.parse("2026-09-12T00:00:00Z"),
                                Instant.parse("2026-09-13T00:00:00Z"),
                                null,
                                null,
                                0,
                                50)
                        .totalElements());

        UUID oldestId =
                listAll(mine).content().get(listAll(mine).content().size() - 1).entryId();
        jdbc.update(
                "UPDATE warehouse.warehouse_operations SET updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse("2026-09-10T12:00:00Z")),
                oldestId);
        WarehouseHistoryPage inclusive =
                list(
                        mine,
                        Instant.parse("2026-09-10T00:00:00Z"),
                        Instant.parse("2026-09-11T00:00:00Z"),
                        null,
                        null,
                        0,
                        50);
        assertTrue(inclusive.content().stream().anyMatch(e -> oldestId.equals(e.entryId())));

        UUID endOfDayId = listAll(mine).content().get(0).entryId();
        jdbc.update(
                "UPDATE warehouse.warehouse_operations SET updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse("2026-09-11T23:59:59Z")),
                endOfDayId);
        WarehouseHistoryPage endOfDayInclusive =
                list(
                        mine,
                        Instant.parse("2026-09-11T00:00:00Z"),
                        Instant.parse("2026-09-12T00:00:00Z"),
                        null,
                        null,
                        0,
                        50);
        assertTrue(
                endOfDayInclusive.content().stream().anyMatch(e -> endOfDayId.equals(e.entryId())));
        WarehouseHistoryPage afterEndOfDay =
                list(
                        mine,
                        Instant.parse("2026-09-12T00:00:00Z"),
                        Instant.parse("2026-09-13T00:00:00Z"),
                        null,
                        null,
                        0,
                        50);
        assertTrue(
                afterEndOfDay.content().stream().noneMatch(e -> endOfDayId.equals(e.entryId())));

        WarehouseHistoryPage page0 = list(mine, FROM, TO_EXCLUSIVE, null, null, 0, 2);
        assertEquals(2, page0.content().size());
        assertTrue(
                page0.content().get(0).occurredAt().compareTo(page0.content().get(1).occurredAt())
                        >= 0);

        assertEquals(
                0, list(mine, FROM, TO_EXCLUSIVE, "неттакого", null, 0, 50).totalElements());

        bundle =
                WarehouseIntegrationTestSupport.createApiBundle(
                        dataSource,
                        CLOCK,
                        denyViewAuth(),
                        fixedUser(USER),
                        WarehouseIntegrationTestSupport.permitAllResponsibility());
        assertThrows(
                AccessDeniedException.class,
                () ->
                        bundle.api()
                                .listHistory(
                                        mine,
                                        new WarehouseHistoryFilter(FROM, TO_EXCLUSIVE, null, null),
                                        0,
                                        50));
    }

    private static WarehouseHistoryEntryView byType(WarehouseHistoryPage page, String type) {
        return page.content().stream()
                .filter(e -> type.equals(e.operationType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing " + type));
    }

    private WarehouseHistoryPage listAll(UUID warehouseId) {
        return list(warehouseId, FROM, TO_EXCLUSIVE, null, null, 0, 50);
    }

    private WarehouseHistoryPage list(
            UUID warehouseId,
            Instant from,
            Instant toExclusive,
            String search,
            String type,
            int page,
            int size) {
        return bundle.api()
                .listHistory(
                        warehouseId,
                        new WarehouseHistoryFilter(from, toExclusive, search, type),
                        page,
                        size);
    }

    private UUID createWarehouse(String code, String name) {
        return bundle.api()
                .createWarehouse(new CreateWarehouseCommand(code, name, true))
                .warehouseId();
    }

    private UUID createCell(UUID warehouseId, String code) {
        return bundle.api()
                .createStorageCell(new CreateStorageCellCommand(warehouseId, code, true))
                .storageCellId();
    }

    private void assign(UUID warehouseId) {
        jdbc.update(
                """
                INSERT INTO warehouse.warehouse_user_responsibility
                    (warehouse_id, user_id, assigned_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                """,
                warehouseId,
                USER);
    }

    private UUID materialId(String article) {
        return jdbc.queryForObject(
                "SELECT id FROM warehouse.material_references WHERE article = ?",
                UUID.class,
                article);
    }

    private BigDecimal available(UUID materialId, UUID cellId) {
        BigDecimal qty =
                jdbc.query(
                        """
                        SELECT quantity FROM warehouse.stock_positions
                         WHERE material_reference_id = ? AND storage_cell_id = ?
                           AND stock_state = ?
                        """,
                        rs -> rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO,
                        materialId,
                        cellId,
                        StockState.AVAILABLE.name());
        return qty == null ? BigDecimal.ZERO : qty;
    }

    private long documentVersion(UUID documentId) {
        return bundle.documentEngine().findById(documentId).orElseThrow().version();
    }

    private long countCompletedOps() {
        Long count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.warehouse_operations WHERE status = 'COMPLETED'",
                        Long.class);
        return count == null ? 0L : count;
    }

    private long countMovements() {
        Long count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.warehouse_movements", Long.class);
        return count == null ? 0L : count;
    }

    private Map<String, Object> snapshotFacts() {
        return Map.of(
                "ops",
                countCompletedOps(),
                "movements",
                countMovements(),
                "stock",
                jdbc.queryForObject("SELECT COUNT(*) FROM warehouse.stock_positions", Long.class));
    }

    private static AuthorizationService allowAllAuth() {
        return new AuthorizationService() {
            @Override
            public boolean hasPermission(PermissionId permissionId) {
                return true;
            }

            @Override
            public void requirePermission(PermissionId permissionId) {}

            @Override
            public Set<PermissionId> effectivePermissions() {
                return Set.of();
            }
        };
    }

    private static AuthorizationService denyViewAuth() {
        return new AuthorizationService() {
            @Override
            public boolean hasPermission(PermissionId permissionId) {
                return false;
            }

            @Override
            public void requirePermission(PermissionId permissionId) {
                throw new AccessDeniedException("denied " + permissionId.value());
            }

            @Override
            public Set<PermissionId> effectivePermissions() {
                return Set.of();
            }
        };
    }

    private static AuthenticationService fixedUser(UUID userId) {
        SessionSummary session =
                new SessionSummary(
                        SessionId.generate(),
                        UserId.of(userId),
                        Login.of("hist-user"),
                        Instant.parse("2026-09-11T10:00:00Z"));
        return new AuthenticationService() {
            @Override
            public SessionSummary login(Login login, char[] password) {
                throw new UnsupportedOperationException();
            }

            @Override
            public SessionSummary completePasswordSetup(
                    Login login,
                    String activationCode,
                    char[] newPassword,
                    char[] confirmPassword) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void logout() {}

            @Override
            public Optional<SessionSummary> currentSession() {
                return Optional.of(session);
            }

            @Override
            public boolean isAuthenticated() {
                return true;
            }
        };
    }
}
