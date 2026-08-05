package com.tmp.order.application.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.OrderManagementAutoConfiguration;
import com.tmp.order.api.OrderDto;
import com.tmp.order.api.OrderItemDto;
import com.tmp.order.api.OrderItemStatus;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.PageRequest;
import com.tmp.order.api.RevisionStatus;
import com.tmp.order.api.imports.OrderImportBatch;
import com.tmp.order.api.imports.OrderImportConfirmResult;
import com.tmp.order.api.imports.OrderImportPreview;
import com.tmp.order.api.imports.OrderImportService;
import com.tmp.order.api.ui.OrderItemEditorQueryService;
import com.tmp.order.api.ui.OrderItemEditorSnapshot;
import com.tmp.order.api.ui.OrderItemSpecificationEditorQueryService;
import com.tmp.order.api.ui.OrderItemSpecificationEditorSnapshot;
import com.tmp.order.api.ui.OrderItemSpecificationLineView;
import com.tmp.order.application.imports.stxt.StxtFileAdapter;
import com.tmp.order.application.imports.stxt.StxtParseResult;
import com.tmp.order.capability.OrderManagementPermissions;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.RoleAdministrationService;
import com.tmp.security.api.UserAdministrationService;
import com.tmp.security.api.UserSummary;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * STAGE5-058 — E2E: STXT → Import Adapter → Import Core → Approve flow → ACTIVE Order / Item /
 * Revision / Specification.
 */
@Testcontainers
@SpringBootTest(classes = OrderIntakeStxtEndToEndIT.TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "tmp.security.bootstrap.admin-login=admin",
            "tmp.security.bootstrap.admin-display-name=Administrator",
            "tmp.security.bootstrap.admin-password=bootstrap-secret-value"
        })
class OrderIntakeStxtEndToEndIT {

    private static final char[] IMPORTER_PASSWORD = "importer-secret-value".toCharArray();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired private OrderImportService orderImportService;
    @Autowired private OrderQueryService orderQueryService;
    @Autowired private OrderItemEditorQueryService itemEditorQueryService;
    @Autowired private OrderItemSpecificationEditorQueryService specificationEditorQueryService;
    @Autowired private AuthenticationService authenticationService;
    @Autowired private UserAdministrationService userAdministrationService;
    @Autowired private RoleAdministrationService roleAdministrationService;
    @Autowired private JdbcTemplate jdbc;

    private final StxtFileAdapter stxtFileAdapter = new StxtFileAdapter();

    @BeforeEach
    void setUp() {
        authenticationService.logout();
        jdbc.update("DELETE FROM order_management.order_document_processing");
        jdbc.update("DELETE FROM order_management.order_document_payload");
        jdbc.update("DELETE FROM order_management.item_specification_lines");
        jdbc.update("DELETE FROM order_management.item_specifications");
        jdbc.update("DELETE FROM order_management.order_item_revisions");
        jdbc.update("DELETE FROM order_management.order_items");
        jdbc.update("DELETE FROM order_management.orders");
        ensureImporter();
    }

    @Test
    void stxtThroughApproveFlowLandsActiveOrderItemRevisionAndSpecification() throws IOException {
        byte[] content = readFixture("stxt/sample-utf8.stxt");
        StxtParseResult parseResult = stxtFileAdapter.parse(content, "sample-utf8.stxt");
        assertTrue(parseResult.isSuccessful(), () -> parseResult.errors().toString());

        OrderImportBatch batch = parseResult.batch().orElseThrow();
        assertEquals("26062891", batch.orderNumber());

        OrderImportPreview preview = orderImportService.preview(batch);
        assertTrue(preview.canConfirm(), () -> preview.errors().toString());
        assertTrue(preview.preparedPlan().isPresent());

        OrderImportConfirmResult confirm =
                orderImportService.confirm(preview.preparedPlan().orElseThrow());
        assertEquals("26062891", confirm.orderNumber());
        assertEquals(2, confirm.createdPositionCount());
        assertEquals(4, confirm.createdSpecificationLineCount());

        OrderDto order = orderQueryService.getOrder(confirm.orderId()).orElseThrow();
        assertEquals("26062891", order.orderNumber());
        assertEquals(OrderStatus.ACTIVE, order.status(), "Order must be ACTIVE after approve+activate");
        assertEquals("Альпы ООО", order.customerName());

        List<OrderItemDto> items =
                orderQueryService.getOrderItems(confirm.orderId(), PageRequest.firstPage()).content();
        assertEquals(2, items.size());
        OrderItemDto firstItem =
                items.stream()
                        .filter(item -> "1".equals(item.externalPositionNumber()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(OrderItemStatus.ACTIVE, firstItem.status(), "Item must be ACTIVE after revision approve");
        assertEquals("1", firstItem.externalPositionNumber());
        assertEquals("WHS_60", firstItem.productCode());
        assertEquals("WHS HALO WHS_60 ActivPilot", firstItem.name());

        OrderItemEditorSnapshot editor =
                itemEditorQueryService.getEditorSnapshot(firstItem.orderItemId()).orElseThrow();
        assertEquals("1", editor.externalPositionNumber());
        assertEquals("WHS_60", editor.productCode());
        assertEquals("WHS HALO WHS_60 ActivPilot", editor.name());
        assertEquals(0, new BigDecimal("8").compareTo(editor.orderedQuantity()));
        assertTrue(editor.activeRevisionNumber().isPresent(), "Revision must be ACTIVE");
        assertTrue(editor.draftRevisionNumber().isEmpty());
        assertEquals(
                RevisionStatus.ACTIVE,
                editor.activeRevision().orElseThrow().status(),
                "Active revision view must report RevisionStatus.ACTIVE");

        OrderItemSpecificationEditorSnapshot specification =
                specificationEditorQueryService
                        .getSpecificationSnapshot(
                                firstItem.orderItemId(), editor.activeRevisionNumber().orElseThrow())
                        .orElseThrow();
        assertEquals(RevisionStatus.ACTIVE, specification.revisionStatus(), "Specification ACTIVE with revision");
        assertTrue(specification.immutable(), "ACTIVE specification must be read-only / immutable");
        assertEquals(0, new BigDecimal("8").compareTo(specification.orderedQuantity()));
        assertEquals(3, specification.lines().size());

        OrderItemSpecificationLineView line = specification.lines().get(0);
        assertEquals("107.225белый", line.materialCode());
        assertEquals("Штапик черный 8 мм/38.39.40", line.materialName());
        assertEquals("Белый", line.color());
        assertEquals(0, new BigDecimal("2066.0").compareTo(line.lengthMm()));
        assertEquals(0, new BigDecimal("16").compareTo(line.lineQuantity()));
        assertEquals("шт.", line.unitOfMeasure());
        assertEquals(
                0,
                new BigDecimal("16")
                        .compareTo(line.lineQuantity()),
                "lineQuantity must not be multiplied by productQuantity");
    }

    @Test
    void multiOrderStxtFixtureLandsAllOrdersActive() throws IOException {
        byte[] content = readFixture("stxt/multi-order.stxt");
        StxtParseResult parseResult = stxtFileAdapter.parse(content, "multi-order.stxt");
        assertTrue(parseResult.isSuccessful(), () -> parseResult.errors().toString());
        assertEquals(2, parseResult.batches().size());

        OrderImportPreview preview = orderImportService.preview(parseResult.batches());
        assertTrue(preview.canConfirm(), () -> preview.errors().toString());

        OrderImportConfirmResult confirm =
                orderImportService.confirm(preview.preparedPlan().orElseThrow());
        assertEquals(2, confirm.createdOrderCount());
        assertTrue(confirm.orderNumber().contains("25096190"));
        assertTrue(confirm.orderNumber().contains("25096053"));

        for (OrderImportConfirmResult.ImportedOrder imported : confirm.orders()) {
            OrderDto order = orderQueryService.getOrder(imported.orderId()).orElseThrow();
            assertEquals(OrderStatus.ACTIVE, order.status());
            List<OrderItemDto> items =
                    orderQueryService
                            .getOrderItems(imported.orderId(), PageRequest.firstPage())
                            .content();
            assertFalse(items.isEmpty());
            for (OrderItemDto item : items) {
                assertEquals(OrderItemStatus.ACTIVE, item.status());
            }
        }
    }

    private void ensureImporter() {
        authenticationService.login(Login.of("admin"), "bootstrap-secret-value".toCharArray());
        UserSummary importer =
                userAdministrationService.listUsers(0, 100, null).stream()
                        .filter(user -> "importer".equalsIgnoreCase(user.login().value()))
                        .findFirst()
                        .orElseGet(
                                () ->
                                        userAdministrationService.createUser(
                                                Login.of("importer"),
                                                DisplayName.of("Importer"),
                                                IMPORTER_PASSWORD.clone()));
        roleAdministrationService.grantIndividualPermission(
                importer.id(), OrderManagementPermissions.ORDER_CREATE);
        roleAdministrationService.grantIndividualPermission(
                importer.id(), OrderManagementPermissions.ORDER_VIEW);
        roleAdministrationService.grantIndividualPermission(
                importer.id(), OrderManagementPermissions.ITEM_VIEW);
        roleAdministrationService.grantIndividualPermission(
                importer.id(), OrderManagementPermissions.ITEM_CREATE);
        roleAdministrationService.grantIndividualPermission(
                importer.id(), OrderManagementPermissions.ITEM_EDIT);
        roleAdministrationService.grantIndividualPermission(
                importer.id(), OrderManagementPermissions.REVISION_CREATE);
        roleAdministrationService.grantIndividualPermission(
                importer.id(), OrderManagementPermissions.REVISION_EDIT);
        roleAdministrationService.grantIndividualPermission(
                importer.id(), OrderManagementPermissions.ITEM_APPROVE);
        roleAdministrationService.grantIndividualPermission(
                importer.id(), OrderManagementPermissions.ORDER_APPROVE);
        roleAdministrationService.grantIndividualPermission(
                importer.id(), OrderManagementPermissions.SPECIFICATION_VIEW);
        authenticationService.logout();
        authenticationService.login(Login.of("importer"), IMPORTER_PASSWORD.clone());
    }

    private static byte[] readFixture(String classpath) throws IOException {
        try (InputStream in =
                OrderIntakeStxtEndToEndIT.class.getClassLoader().getResourceAsStream(classpath)) {
            if (in == null) {
                throw new IOException("Missing fixture: " + classpath);
            }
            return in.readAllBytes();
        }
    }

    @SpringBootApplication
    @Import({
        com.tmp.infra.db.DatabaseAutoConfiguration.class,
        com.tmp.core.PlatformCoreAutoConfiguration.class,
        com.tmp.document.DocumentEngineAutoConfiguration.class,
        com.tmp.capability.CapabilityEngineAutoConfiguration.class,
        com.tmp.security.SecurityAutoConfiguration.class,
        OrderManagementAutoConfiguration.class
    })
    static class TestApplication {}
}
