package com.tmp.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.bootstrap.AbstractBootstrapPostgresSpringTest;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.Login;
import com.tmp.warehouse.api.TransferDocumentOrderReferenceQuery;
import com.tmp.warehouse.api.WarehouseApi;
import com.tmp.warehouse.api.WarehouseApi.CreateTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.SendTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentLineInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentSourceAllocationInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskKind;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskView;
import com.tmp.warehouse.api.WarehouseDemandCommandApi;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.AcceptProductionDemandCommand;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.AcceptProductionDemandResult;
import com.tmp.warehouse.api.WarehouseDemandCommandApi.ProductionDemandLine;
import com.tmp.warehouse.application.WarehouseOperationalInboxService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * B3-MA-FIX3: reproduces Warehouse UI task list query ({@link WarehouseApi#listMyWarehouseTasks})
 * with composition-bound order enrichment wired like production bootstrap.
 */
@SpringBootTest
@ActiveProfiles("test")
class WarehouseTaskOrderNumberRuntimePathIT extends AbstractBootstrapPostgresSpringTest {

    private static final char[] ADMIN_PASSWORD = "test-admin-password".toCharArray();

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private WarehouseApi warehouseApi;

    @Autowired
    private WarehouseDemandCommandApi demandCommandApi;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void loginAsAdmin() {
        authenticationService.logout();
        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());
        clearWarehouseFacts();
    }

    @Test
    void compositionOrderReferenceQueryBeanIsRegisteredBeforeOperationalInbox() {
        assertNotNull(applicationContext.getBean(TransferDocumentOrderReferenceQuery.class));
        WarehouseOperationalInboxService inbox =
                applicationContext.getBean(WarehouseOperationalInboxService.class);
        assertNotNull(inbox);
    }

    @Test
    void transferPreparationFromDemandShowsHumanOrderNumberViaListMyWarehouseTasks() {
        UUID workerId =
                authenticationService
                        .currentSession()
                        .orElseThrow()
                        .userId()
                        .value();
        UUID orderId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        UUID requirementLineId = UUID.randomUUID();
        insertOrder("TEST-002", orderId);
        insertMaterialRequirementProvenance(
                requirementId, requirementLineId, orderId, orderItemId);

        UUID sourceWarehouseId = UUID.randomUUID();
        UUID destWarehouseId = UUID.randomUUID();
        UUID sourceCellId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        insertWarehouseStructure(
                sourceWarehouseId, destWarehouseId, sourceCellId, materialId, workerId);
        seedStock(sourceCellId, materialId, "50");

        AcceptProductionDemandResult accepted =
                demandCommandApi.acceptProductionDemand(
                        new AcceptProductionDemandCommand(
                                requirementId,
                                destWarehouseId,
                                "admin",
                                List.of(
                                        new ProductionDemandLine(
                                                requirementLineId,
                                                "MAT-001",
                                                "Тестовый материал",
                                                "",
                                                "шт.",
                                                null,
                                                new BigDecimal("6"),
                                                materialId))));

        assertEquals(1, accepted.documents().size());

        List<WarehouseTaskView> tasks = warehouseApi.listMyWarehouseTasks(null);
        WarehouseTaskView preparation =
                tasks.stream()
                        .filter(t -> t.taskKind() == WarehouseTaskKind.TRANSFER_PREPARATION)
                        .findFirst()
                        .orElseThrow();
        assertEquals("TEST-002", preparation.sourceOrderNumber());
    }

    @Test
    void supplyTaskShowsOrderNumberWhenMaterialUnresolved() {
        UUID workerId =
                authenticationService
                        .currentSession()
                        .orElseThrow()
                        .userId()
                        .value();
        UUID orderId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        UUID requirementLineId = UUID.randomUUID();
        insertOrder("TEST-SUPPLY-001", orderId);
        insertMaterialRequirementProvenance(
                requirementId, requirementLineId, orderId, orderItemId);

        UUID sourceWarehouseId = UUID.randomUUID();
        UUID destWarehouseId = UUID.randomUUID();
        insertWarehouseStructure(
                sourceWarehouseId, destWarehouseId, UUID.randomUUID(), UUID.randomUUID(), workerId);

        demandCommandApi.acceptProductionDemand(
                new AcceptProductionDemandCommand(
                        requirementId,
                        destWarehouseId,
                        "admin",
                        List.of(
                                new ProductionDemandLine(
                                        requirementLineId,
                                        "UNKNOWN-MAT",
                                        "Unknown",
                                        "",
                                        "шт.",
                                        null,
                                        new BigDecimal("3"),
                                        null))));

        List<WarehouseTaskView> tasks = warehouseApi.listMyWarehouseTasks(null);
        WarehouseTaskView supply =
                tasks.stream()
                        .filter(
                                t ->
                                        t.taskKind()
                                                == WarehouseTaskKind.PRODUCTION_MATERIAL_SUPPLY)
                        .findFirst()
                        .orElseThrow();
        assertEquals("TEST-SUPPLY-001", supply.sourceOrderNumber());
    }

    @Test
    void transferReceiptKeepsHumanOrderNumberViaListMyWarehouseTasks() {
        UUID workerId =
                authenticationService
                        .currentSession()
                        .orElseThrow()
                        .userId()
                        .value();
        UUID orderId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        UUID requirementLineId = UUID.randomUUID();
        insertOrder("TEST-002", orderId);
        insertMaterialRequirementProvenance(
                requirementId, requirementLineId, orderId, orderItemId);

        UUID sourceWarehouseId = UUID.randomUUID();
        UUID destWarehouseId = UUID.randomUUID();
        UUID sourceCellId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        insertWarehouseStructure(
                sourceWarehouseId, destWarehouseId, sourceCellId, materialId, workerId);
        jdbc.update(
                """
                INSERT INTO warehouse.warehouse_user_responsibility (
                    warehouse_id, user_id, assigned_at)
                VALUES (?, ?, NOW())
                """,
                destWarehouseId,
                workerId);
        seedStock(sourceCellId, materialId, "50");

        AcceptProductionDemandResult accepted =
                demandCommandApi.acceptProductionDemand(
                        new AcceptProductionDemandCommand(
                                requirementId,
                                destWarehouseId,
                                "admin",
                                List.of(
                                        new ProductionDemandLine(
                                                requirementLineId,
                                                "MAT-001",
                                                "Тестовый материал",
                                                "",
                                                "шт.",
                                                null,
                                                new BigDecimal("6"),
                                                materialId))));
        UUID documentId = accepted.documents().getFirst().documentId();

        TransferDocumentView loaded = warehouseApi.getTransferDocument(documentId);
        warehouseApi.takeTransferTaskInWork(documentId);
        loaded = warehouseApi.getTransferDocument(documentId);
        warehouseApi.sendTransferDocument(
                new SendTransferDocumentCommand(
                        documentId,
                        loaded.documentVersion(),
                        loaded.payloadRevision(),
                        List.of(
                                new TransferDocumentSourceAllocationInput(
                                        loaded.lines().getFirst().lineId(),
                                        sourceCellId,
                                        new BigDecimal("6")))));

        List<WarehouseTaskView> tasks = warehouseApi.listMyWarehouseTasks(null);
        WarehouseTaskView receipt =
                tasks.stream()
                        .filter(t -> t.taskKind() == WarehouseTaskKind.TRANSFER_RECEIPT)
                        .findFirst()
                        .orElseThrow();
        assertEquals("TEST-002", receipt.sourceOrderNumber());
    }

    @Test
    void manualTransferWithoutProductionLineageShowsBlankOrderNumber() {
        UUID workerId =
                authenticationService
                        .currentSession()
                        .orElseThrow()
                        .userId()
                        .value();
        UUID sourceWarehouseId = UUID.randomUUID();
        UUID destWarehouseId = UUID.randomUUID();
        UUID sourceCellId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        insertWarehouseStructure(
                sourceWarehouseId, destWarehouseId, sourceCellId, materialId, workerId);

        warehouseApi.createTransferDocument(
                new CreateTransferDocumentCommand(
                        sourceWarehouseId,
                        destWarehouseId,
                        List.of(
                                new TransferDocumentLineInput(
                                        null, materialId, BigDecimal.TEN, 1))));

        List<WarehouseTaskView> tasks = warehouseApi.listMyWarehouseTasks(null);
        WarehouseTaskView preparation =
                tasks.stream()
                        .filter(t -> t.taskKind() == WarehouseTaskKind.TRANSFER_PREPARATION)
                        .findFirst()
                        .orElseThrow();
        assertTrue(
                preparation.sourceOrderNumber() == null
                        || preparation.sourceOrderNumber().isBlank());
    }

    @Test
    void crossOrderMaterialRequirementComposesDeterministicOrderNumbers() {
        UUID workerId =
                authenticationService
                        .currentSession()
                        .orElseThrow()
                        .userId()
                        .value();
        UUID order101 = UUID.randomUUID();
        UUID order102 = UUID.randomUUID();
        UUID item101 = UUID.randomUUID();
        UUID item102 = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        UUID line101 = UUID.randomUUID();
        UUID line102 = UUID.randomUUID();
        insertOrder("ORDER-102", order102);
        insertOrder("ORDER-101", order101);
        insertCrossOrderProvenance(
                requirementId, line101, line102, order101, order102, item101, item102);

        UUID sourceWarehouseId = UUID.randomUUID();
        UUID destWarehouseId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();
        UUID matA = UUID.randomUUID();
        UUID matB = UUID.randomUUID();
        insertWarehouseStructure(sourceWarehouseId, destWarehouseId, cellId, matA, workerId);
        jdbc.update(
                """
                INSERT INTO warehouse.material_references (
                    id, article, name, color, size, unit_of_measure, created_at, updated_at)
                VALUES (?, 'B', 'B', '', '', 'шт.', NOW(), NOW())
                """,
                matB);
        seedStock(cellId, matA, "10");
        seedStock(cellId, matB, "10");

        demandCommandApi.acceptProductionDemand(
                new AcceptProductionDemandCommand(
                        requirementId,
                        destWarehouseId,
                        "admin",
                        List.of(
                                new ProductionDemandLine(
                                        line101,
                                        "A",
                                        "A",
                                        "",
                                        "шт.",
                                        null,
                                        BigDecimal.ONE,
                                        matA),
                                new ProductionDemandLine(
                                        line102,
                                        "B",
                                        "B",
                                        "",
                                        "шт.",
                                        null,
                                        BigDecimal.ONE,
                                        matB))));

        List<WarehouseTaskView> tasks = warehouseApi.listMyWarehouseTasks(null);
        assertTrue(tasks.stream().anyMatch(t -> "ORDER-101, ORDER-102".equals(t.sourceOrderNumber())));
    }

    private void clearWarehouseFacts() {
        jdbc.update("DELETE FROM warehouse.demand_task_state");
        jdbc.update("DELETE FROM warehouse.transfer_return_settlement_item");
        jdbc.update("DELETE FROM warehouse.transfer_receipt_settlement_item");
        jdbc.update("DELETE FROM warehouse.transfer_document_settlement");
        jdbc.update("DELETE FROM warehouse.transfer_document_send_allocation");
        jdbc.update("DELETE FROM warehouse.transfer_operation_context");
        jdbc.update("DELETE FROM warehouse.warehouse_movements");
        jdbc.update("DELETE FROM warehouse.warehouse_operations");
        jdbc.update("DELETE FROM warehouse.warehouse_demand_transfer_links");
        jdbc.update("DELETE FROM warehouse.warehouse_demand_lines");
        jdbc.update("DELETE FROM warehouse.warehouse_demands");
        jdbc.update("DELETE FROM warehouse.transfer_document_lines");
        jdbc.update("DELETE FROM warehouse.transfer_document_payload");
        jdbc.update("DELETE FROM documents.document_lifecycle_journal");
        jdbc.update("DELETE FROM documents.document_versions");
        jdbc.update("DELETE FROM documents.documents");
        jdbc.update("DELETE FROM warehouse.transfer_task_state");
        jdbc.update("DELETE FROM warehouse.warehouse_user_responsibility");
        jdbc.update("DELETE FROM warehouse.stock_positions");
        jdbc.update("DELETE FROM warehouse.storage_cells");
        jdbc.update("DELETE FROM warehouse.warehouses");
        jdbc.update("DELETE FROM warehouse.material_references");
        jdbc.update("DELETE FROM production.material_requirement_line_source_items");
        jdbc.update("DELETE FROM production.material_requirement_lines");
        jdbc.update("DELETE FROM production.material_requirement_source_items");
        jdbc.update("DELETE FROM production.material_requirements");
        jdbc.update("DELETE FROM order_management.order_items");
        jdbc.update("DELETE FROM order_management.orders");
    }

    private void insertOrder(String orderNumber, UUID orderId) {
        Timestamp now = Timestamp.from(Instant.parse("2026-10-07T07:50:00Z"));
        jdbc.update(
                """
                INSERT INTO order_management.orders (
                    order_id, order_number, customer_name, direction, currency, status,
                    version, created_at, updated_at)
                VALUES (?, ?, 'Customer', 'PRIVATE', 'RUB', 'APPROVED', 0, ?, ?)
                """,
                orderId,
                orderNumber,
                now,
                now);
    }

    private void insertMaterialRequirementProvenance(
            UUID requirementId,
            UUID requirementLineId,
            UUID orderId,
            UUID orderItemId) {
        Timestamp now = Timestamp.from(Instant.parse("2026-10-07T07:50:00Z"));
        UUID destinationWarehouseId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO production.material_requirements (
                    id, source_order_id, destination_warehouse_id, status, version,
                    created_at, updated_at, submitted_at, submitted_by)
                VALUES (?, ?, ?, 'SUBMITTED', 0, ?, ?, ?, 'admin')
                """,
                requirementId,
                orderId,
                destinationWarehouseId,
                now,
                now,
                now);
        jdbc.update(
                """
                INSERT INTO production.material_requirement_source_items (
                    requirement_id, source_order_id, source_order_item_id,
                    requested_product_quantity, counts_toward_product_coverage)
                VALUES (?, ?, ?, 1, TRUE)
                """,
                requirementId,
                orderId,
                orderItemId);
        jdbc.update(
                """
                INSERT INTO production.material_requirement_lines (
                    id, requirement_id, material_reference_id, material_code, material_name,
                    color, unit_of_measure, length_mm, quantity, line_order)
                VALUES (?, ?, NULL, 'MAT-001', 'Тестовый материал', '', 'шт.', NULL, 6, 0)
                """,
                requirementLineId,
                requirementId);
        jdbc.update(
                """
                INSERT INTO production.material_requirement_line_source_items (
                    line_id, source_order_item_id, source_order_id, contributed_material_quantity)
                VALUES (?, ?, ?, 6)
                """,
                requirementLineId,
                orderItemId,
                orderId);
    }

    private void insertCrossOrderProvenance(
            UUID requirementId,
            UUID line101,
            UUID line102,
            UUID order101,
            UUID order102,
            UUID item101,
            UUID item102) {
        Timestamp now = Timestamp.from(Instant.parse("2026-10-07T07:50:00Z"));
        UUID destinationWarehouseId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO production.material_requirements (
                    id, source_order_id, destination_warehouse_id, status, version,
                    created_at, updated_at, submitted_at, submitted_by)
                VALUES (?, NULL, ?, 'SUBMITTED', 0, ?, ?, ?, 'admin')
                """,
                requirementId,
                destinationWarehouseId,
                now,
                now,
                now);
        jdbc.update(
                """
                INSERT INTO production.material_requirement_source_items (
                    requirement_id, source_order_id, source_order_item_id,
                    requested_product_quantity, counts_toward_product_coverage)
                VALUES (?, ?, ?, 1, TRUE)
                """,
                requirementId,
                order101,
                item101);
        jdbc.update(
                """
                INSERT INTO production.material_requirement_source_items (
                    requirement_id, source_order_id, source_order_item_id,
                    requested_product_quantity, counts_toward_product_coverage)
                VALUES (?, ?, ?, 1, TRUE)
                """,
                requirementId,
                order102,
                item102);
        jdbc.update(
                """
                INSERT INTO production.material_requirement_lines (
                    id, requirement_id, material_reference_id, material_code, material_name,
                    color, unit_of_measure, length_mm, quantity, line_order)
                VALUES (?, ?, NULL, 'A', 'A', '', 'шт.', NULL, 1, 0)
                """,
                line101,
                requirementId);
        jdbc.update(
                """
                INSERT INTO production.material_requirement_lines (
                    id, requirement_id, material_reference_id, material_code, material_name,
                    color, unit_of_measure, length_mm, quantity, line_order)
                VALUES (?, ?, NULL, 'B', 'B', '', 'шт.', NULL, 1, 1)
                """,
                line102,
                requirementId);
        jdbc.update(
                """
                INSERT INTO production.material_requirement_line_source_items (
                    line_id, source_order_item_id, source_order_id, contributed_material_quantity)
                VALUES (?, ?, ?, 1)
                """,
                line101,
                item101,
                order101);
        jdbc.update(
                """
                INSERT INTO production.material_requirement_line_source_items (
                    line_id, source_order_item_id, source_order_id, contributed_material_quantity)
                VALUES (?, ?, ?, 1)
                """,
                line102,
                item102,
                order102);
    }

    private void insertWarehouseStructure(
            UUID sourceWarehouseId,
            UUID destWarehouseId,
            UUID sourceCellId,
            UUID materialId,
            UUID workerId) {
        jdbc.update(
                """
                INSERT INTO warehouse.warehouses (
                    id, code, name, active, is_production, version, created_at, updated_at)
                VALUES (?, 'SRC', 'Source', TRUE, FALSE, 0, NOW(), NOW())
                """,
                sourceWarehouseId);
        jdbc.update(
                """
                INSERT INTO warehouse.warehouses (
                    id, code, name, active, is_production, version, created_at, updated_at)
                VALUES (?, 'DEST', 'Destination', TRUE, TRUE, 0, NOW(), NOW())
                """,
                destWarehouseId);
        jdbc.update(
                """
                INSERT INTO warehouse.storage_cells (
                    id, warehouse_id, code, active, version, created_at, updated_at)
                VALUES (?, ?, 'S-1', TRUE, 0, NOW(), NOW())
                """,
                sourceCellId,
                sourceWarehouseId);
        jdbc.update(
                """
                INSERT INTO warehouse.material_references (
                    id, article, name, color, size, unit_of_measure, created_at, updated_at)
                VALUES (?, 'MAT-001', 'Тестовый материал', '', '', 'шт.', NOW(), NOW())
                """,
                materialId);
        jdbc.update(
                """
                INSERT INTO warehouse.warehouse_user_responsibility (
                    warehouse_id, user_id, assigned_at)
                VALUES (?, ?, NOW())
                """,
                sourceWarehouseId,
                workerId);
    }

    private void seedStock(UUID cellId, UUID materialId, String quantity) {
        UUID warehouseId =
                jdbc.queryForObject(
                        "SELECT warehouse_id FROM warehouse.storage_cells WHERE id = ?",
                        UUID.class,
                        cellId);
        jdbc.update(
                """
                INSERT INTO warehouse.stock_positions (
                    id, warehouse_id, storage_cell_id, material_reference_id, quantity,
                    stock_state, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?::numeric, 'AVAILABLE', 0, NOW(), NOW())
                """,
                UUID.randomUUID(),
                warehouseId,
                cellId,
                materialId,
                quantity);
    }
}
