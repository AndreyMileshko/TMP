package com.tmp.order.application.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.OrderManagementAutoConfiguration;
import com.tmp.order.api.OrderDto;
import com.tmp.order.api.OrderItemDto;
import com.tmp.order.api.OrderItemStatus;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.PageRequest;
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
 * STAGE5-056 — full Order Intake path: STXT fixture → adapter → preview → confirm → DRAFT
 * structure → Query API / UI editor reads.
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
        jdbc.update("DELETE FROM order_management.order_import_metadata");
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
    void stxtFixtureThroughConfirmIsReadableAsIncompleteDraft() throws IOException {
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
        assertEquals(3, confirm.createdSpecificationLineCount());

        OrderDto order = orderQueryService.getOrder(confirm.orderId()).orElseThrow();
        assertEquals("26062891", order.orderNumber());
        assertEquals(OrderStatus.DRAFT, order.status());

        List<OrderItemDto> items =
                orderQueryService.getOrderItems(confirm.orderId(), PageRequest.firstPage()).content();
        assertEquals(2, items.size());
        OrderItemDto firstItem =
                items.stream()
                        .filter(item -> "1".equals(item.externalPositionNumber()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(OrderItemStatus.DRAFT, firstItem.status());
        assertEquals("1", firstItem.externalPositionNumber());
        assertNull(firstItem.productCode());
        assertNull(firstItem.name());

        OrderItemEditorSnapshot editor =
                itemEditorQueryService.getEditorSnapshot(firstItem.orderItemId()).orElseThrow();
        assertEquals("1", editor.externalPositionNumber());
        assertNull(editor.productCode());
        assertNull(editor.name());
        assertEquals(0, new BigDecimal("8").compareTo(editor.orderedQuantity()));
        assertTrue(editor.draftRevisionNumber().isPresent());

        OrderItemSpecificationEditorSnapshot specification =
                specificationEditorQueryService
                        .getSpecificationSnapshot(
                                firstItem.orderItemId(), editor.draftRevisionNumber().orElseThrow())
                        .orElseThrow();
        assertEquals(0, new BigDecimal("8").compareTo(specification.orderedQuantity()));
        assertEquals(2, specification.lines().size());

        OrderItemSpecificationLineView line = specification.lines().get(0);
        assertEquals("107.225белый", line.materialCode());
        assertEquals("Штапик черный 8 мм/38.39.40", line.materialName());
        assertEquals("Белый", line.color());
        assertEquals(0, new BigDecimal("2066.0").compareTo(line.lengthMm()));
        assertEquals(0, new BigDecimal("16").compareTo(line.lineQuantity()));
        assertEquals(
                0,
                new BigDecimal("16")
                        .compareTo(line.lineQuantity()),
                "lineQuantity must not be multiplied by productQuantity");
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
