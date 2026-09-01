package com.tmp.order.application.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.order.OrderManagementAutoConfiguration;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemStatus;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.RevisionStatus;
import com.tmp.order.api.imports.OrderImportBatch;
import com.tmp.order.api.imports.OrderImportConfirmResult;
import com.tmp.order.api.imports.OrderImportConflictException;
import com.tmp.order.api.imports.OrderImportPosition;
import com.tmp.order.api.imports.OrderImportPreview;
import com.tmp.order.api.imports.OrderImportService;
import com.tmp.order.api.imports.OrderImportSpecificationLine;
import com.tmp.order.api.imports.PreparedOrderImportPlan;
import com.tmp.order.application.payload.DocumentId;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.OrderCreatePayload;
import com.tmp.order.capability.OrderManagementPermissions;
import com.tmp.order.domain.CustomerOrder;
import com.tmp.order.domain.OrderCommercialData;
import com.tmp.order.domain.OrderItem;
import com.tmp.order.domain.OrderNumber;
import com.tmp.order.domain.OrderedQuantity;
import com.tmp.order.domain.SpecificationLine;
import com.tmp.order.domain.repository.CustomerOrderRepository;
import com.tmp.order.domain.repository.OrderItemRepository;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.RoleAdministrationService;
import com.tmp.security.api.UserAdministrationService;
import com.tmp.security.api.UserSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
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
 * STAGE5-058 — Import Core PostgreSQL integration: ACTIVE landing, preview isolation, atomic
 * rollback, order-number conflict protection (no import-metadata).
 */
@Testcontainers
@SpringBootTest(classes = OrderImportCoreIT.TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "tmp.security.bootstrap.admin-login=admin",
            "tmp.security.bootstrap.admin-display-name=Administrator",
            "tmp.security.bootstrap.admin-password=bootstrap-secret-value"
        })
class OrderImportCoreIT {

    private static final char[] IMPORTER_PASSWORD = "importer-secret-value".toCharArray();
    private static final Instant NOW = Instant.parse("2026-07-31T05:00:00Z");

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
    @Autowired private AuthenticationService authenticationService;
    @Autowired private UserAdministrationService userAdministrationService;
    @Autowired private RoleAdministrationService roleAdministrationService;
    @Autowired private CustomerOrderRepository orders;
    @Autowired private OrderItemRepository items;
    @Autowired private DocumentEngine documentEngine;
    @Autowired private com.tmp.order.application.payload.OrderDocumentPayloadPort payloads;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private FailingItemSaveToggle failingItemSaveToggle;

    @BeforeEach
    void setUp() {
        failingItemSaveToggle.failNextSave.set(false);
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
    void confirmCreatesActiveOrderItemRevisionAndImmutableSpecification() {
        OrderImportBatch batch = sampleBatch("IMP-OK-1", "checksum-ok-1", "file-a.stxt");
        OrderImportPreview preview = orderImportService.preview(batch);
        assertTrue(preview.canConfirm());
        assertEquals(0, countOrders());
        assertEquals(0, countItems());
        assertEquals(0, countSpecLines());
        assertEquals(0, countProcessing());
        assertFalse(importMetadataTableExists());

        OrderImportConfirmResult result =
                orderImportService.confirm(preview.preparedPlan().orElseThrow());

        assertEquals("IMP-OK-1", result.orderNumber());
        assertEquals(1, result.createdPositionCount());
        assertEquals(2, result.createdSpecificationLineCount());

        CustomerOrder order = orders.findById(result.orderId()).orElseThrow();
        assertEquals(OrderStatus.ACTIVE, order.status());
        assertEquals("Import Customer", order.commercialData().customerName());
        assertNull(order.commercialData().direction());
        assertNull(order.commercialData().currency());
        assertNull(order.commercialData().contractRef());
        assertNull(order.commercialData().siteRef());

        List<OrderItem> orderItems = items.findByOrderId(order.id());
        assertEquals(1, orderItems.size());
        OrderItem item = orderItems.get(0);
        assertEquals(OrderItemStatus.ACTIVE, item.status());
        assertEquals("1", item.commercialData().externalPositionNumber());
        assertEquals("107.225", item.commercialData().productCode().value());
        assertEquals("Штапик product", item.commercialData().name());
        assertTrue(item.activeRevisionNumber().isPresent());
        assertTrue(item.draftRevisionNumber().isEmpty());

        var revision = item.activeRevision().orElseThrow();
        assertEquals(RevisionStatus.ACTIVE, revision.status());
        assertEquals(0, OrderedQuantity.of(8).value().compareTo(revision.orderedQuantity().value()));
        List<SpecificationLine> lines = revision.specification().orElseThrow().lines();
        assertEquals(2, lines.size());
        assertEquals(0, new BigDecimal("16").compareTo(lines.get(0).lineQuantity()));
        assertNull(lines.get(0).color());
        assertNull(lines.get(1).lengthMm());
        assertEquals(OrderImportDefaults.UNIT_OF_MEASURE, lines.get(0).unitOfMeasure());
        assertTrue(revision.specification().orElseThrow().isImmutable());
        assertFalse(importMetadataTableExists());
        assertTrue(countProcessing() >= 6);
    }

    @Test
    void previewLeavesNoPersistence() {
        OrderImportBatch batch = sampleBatch("IMP-PREV-1", "checksum-prev-1", "prev.stxt");
        orderImportService.preview(batch);
        assertEquals(0, countOrders());
        assertEquals(0, countItems());
        assertEquals(0, countRevisions());
        assertEquals(0, countSpecLines());
        assertEquals(0, countProcessing());
        assertEquals(0, countPayloads());
        assertFalse(importMetadataTableExists());
    }

    @Test
    void confirmRollbackOnTestOnlyItemActivationFailureLeavesNoPartialData() {
        OrderImportBatch batch = sampleBatch("IMP-RB-1", "checksum-rb-1", "rb.stxt");
        PreparedOrderImportPlan plan =
                orderImportService.preview(batch).preparedPlan().orElseThrow();
        int documentsBefore = countDocuments();
        failingItemSaveToggle.failNextSave.set(true);

        RuntimeException thrown =
                assertThrows(RuntimeException.class, () -> orderImportService.confirm(plan));
        assertFalse(String.valueOf(thrown.getMessage()).toLowerCase().contains("sql"));
        assertEquals(0, countOrders());
        assertEquals(0, countItems());
        assertEquals(0, countRevisions());
        assertEquals(0, countSpecLines());
        assertEquals(0, countPayloads());
        assertEquals(0, countProcessing());
        assertEquals(documentsBefore, countDocuments());
        assertFalse(importMetadataTableExists());
    }

    @Test
    void confirmAfterPreviewOrderNumberRaceYieldsControlledConflict() {
        OrderImportBatch batch = sampleBatch("IMP-NUM-RACE-1", "checksum-num-race", "num-race.stxt");
        PreparedOrderImportPlan plan =
                orderImportService.preview(batch).preparedPlan().orElseThrow();

        OrderId existingId = seedExistingOrder("IMP-NUM-RACE-1");
        String originalCustomer =
                orders.findById(existingId).orElseThrow().commercialData().customerName();
        int itemsBefore = countItems();

        OrderImportConflictException conflict =
                assertThrows(
                        OrderImportConflictException.class, () -> orderImportService.confirm(plan));
        assertEquals(OrderImportConflictException.USER_MESSAGE, conflict.getMessage());
        assertFalse(conflict.getMessage().toLowerCase().contains("sql"));
        assertEquals(
                originalCustomer,
                orders.findById(existingId).orElseThrow().commercialData().customerName());
        assertEquals(itemsBefore, countItems());
        assertEquals(1, countOrders());
    }

    @Test
    void existingOrderNumberYieldsControlledConflictWithoutMerge() {
        OrderId existingId = seedExistingOrder("IMP-CF-1");
        String originalCustomer =
                orders.findById(existingId).orElseThrow().commercialData().customerName();
        int itemsBefore = countItems();

        OrderImportBatch batch = sampleBatch("IMP-CF-1", "checksum-cf-1", "cf.stxt");
        OrderImportConflictException conflict =
                assertThrows(
                        OrderImportConflictException.class, () -> orderImportService.preview(batch));
        assertEquals(OrderImportConflictException.USER_MESSAGE, conflict.getMessage());
        assertFalse(conflict.getMessage().toLowerCase().contains("sql"));

        assertEquals(existingId, orders.findById(existingId).orElseThrow().id());
        assertEquals(
                originalCustomer,
                orders.findById(existingId).orElseThrow().commercialData().customerName());
        assertEquals(itemsBefore, countItems());
        assertFalse(importMetadataTableExists());
    }

    @Test
    void sameOrderNumberDifferentChecksumYieldsOrderConflict() {
        OrderImportBatch first = sampleBatch("IMP-NUM-1", "checksum-num-1", "a.stxt");
        orderImportService.confirm(orderImportService.preview(first).preparedPlan().orElseThrow());

        OrderImportBatch second = sampleBatch("IMP-NUM-1", "checksum-num-2", "b.stxt");
        assertThrows(OrderImportConflictException.class, () -> orderImportService.preview(second));
        assertEquals(1, countOrders());
    }

    @Test
    void duplicateChecksumWithDifferentOrderNumberIsAllowed() {
        OrderImportBatch first = sampleBatch("IMP-DUP-1", "checksum-shared", "first.stxt");
        orderImportService.confirm(orderImportService.preview(first).preparedPlan().orElseThrow());
        assertEquals(1, countOrders());

        OrderImportBatch second = sampleBatch("IMP-DUP-2", "checksum-shared", "second.stxt");
        OrderImportPreview preview = orderImportService.preview(second);
        assertTrue(preview.canConfirm());
        orderImportService.confirm(preview.preparedPlan().orElseThrow());
        assertEquals(2, countOrders());
    }

    private void ensureImporter() {
        authenticationService.login(Login.of("admin"), "bootstrap-secret-value".toCharArray());
        Optional<UserSummary> existing =
                userAdministrationService.listUsers(0, 100, null).stream()
                        .filter(user -> "importer".equalsIgnoreCase(user.login().value()))
                        .findFirst();
        String activationCode = null;
        UserSummary importer;
        if (existing.isPresent()) {
            importer = existing.get();
        } else {
            var creation =
                    userAdministrationService.createUser(
                            Login.of("importer"), DisplayName.of("Importer"));
            importer = creation.user();
            activationCode = creation.activationCode();
        }
        roleAdministrationService.grantIndividualPermission(
                importer.id(), OrderManagementPermissions.ORDER_CREATE);
        roleAdministrationService.grantIndividualPermission(
                importer.id(), OrderManagementPermissions.ITEM_CREATE);
        roleAdministrationService.grantIndividualPermission(
                importer.id(), OrderManagementPermissions.REVISION_EDIT);
        roleAdministrationService.grantIndividualPermission(
                importer.id(), OrderManagementPermissions.ITEM_APPROVE);
        roleAdministrationService.grantIndividualPermission(
                importer.id(), OrderManagementPermissions.ORDER_APPROVE);
        authenticationService.logout();
        if (existing.isPresent()) {
            authenticationService.login(Login.of("importer"), IMPORTER_PASSWORD.clone());
        } else {
            authenticationService.completePasswordSetup(
                    Login.of("importer"),
                    activationCode,
                    IMPORTER_PASSWORD.clone(),
                    IMPORTER_PASSWORD.clone());
        }
    }

    private OrderId seedExistingOrder(String orderNumber) {
        DocumentMetadata created =
                documentEngine.createDocument(
                        new CreateDocumentCommand(DocumentTypeCode.ORDER_CREATE.name(), "seed"));
        payloads.create(
                OrderCreatePayload.create(
                        DocumentId.of(created.id()),
                        OrderNumber.of(orderNumber),
                        OrderCommercialData.of(
                                null, "Existing Customer", "C-1", "S-1", null, null, null),
                        NOW));
        documentEngine.postDocument(created.id());
        UUID orderUuid =
                jdbc.queryForObject(
                        """
                        SELECT order_id FROM order_management.orders
                        WHERE order_number = ?
                        """,
                        UUID.class,
                        orderNumber);
        return OrderId.of(orderUuid);
    }

    private static OrderImportBatch sampleBatch(
            String orderNumber, String checksum, String sourceReference) {
        return OrderImportBatch.of(
                "STXT",
                sourceReference,
                checksum,
                orderNumber,
                LocalDate.of(2026, 6, 25),
                null,
                "Import Customer",
                List.of(
                        OrderImportPosition.of(
                                "1",
                                "107.225",
                                "Штапик product",
                                8,
                                List.of(
                                        OrderImportSpecificationLine.of(
                                                "107.225",
                                                "Штапик",
                                                null,
                                                new BigDecimal("2066"),
                                                "шт",
                                                new BigDecimal("16")),
                                        OrderImportSpecificationLine.of(
                                                "200.1",
                                                "Профиль",
                                                " ",
                                                null,
                                                "шт",
                                                new BigDecimal("4"))))));
    }

    private boolean importMetadataTableExists() {
        Boolean exists =
                jdbc.queryForObject(
                        """
                        SELECT EXISTS (
                            SELECT 1 FROM information_schema.tables
                            WHERE table_schema = 'order_management'
                              AND table_name = 'order_import_metadata'
                        )
                        """,
                        Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    private int countOrders() {
        return count("SELECT COUNT(*) FROM order_management.orders");
    }

    private int countItems() {
        return count("SELECT COUNT(*) FROM order_management.order_items");
    }

    private int countRevisions() {
        return count("SELECT COUNT(*) FROM order_management.order_item_revisions");
    }

    private int countSpecLines() {
        return count("SELECT COUNT(*) FROM order_management.item_specification_lines");
    }

    private int countProcessing() {
        return count("SELECT COUNT(*) FROM order_management.order_document_processing");
    }

    private int countPayloads() {
        return count("SELECT COUNT(*) FROM order_management.order_document_payload");
    }

    private int countDocuments() {
        return count("SELECT COUNT(*) FROM documents.documents");
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    static final class FailingItemSaveToggle {
        final AtomicBoolean failNextSave = new AtomicBoolean(false);
    }

    static final class ToggleableOrderItemRepository implements OrderItemRepository {

        private final OrderItemRepository delegate;
        private final FailingItemSaveToggle toggle;

        ToggleableOrderItemRepository(OrderItemRepository delegate, FailingItemSaveToggle toggle) {
            this.delegate = delegate;
            this.toggle = toggle;
        }

        @Override
        public OrderItem save(OrderItem item) {
            if (toggle.failNextSave.compareAndSet(true, false)) {
                throw new IllegalStateException("test-only item activation failure");
            }
            return delegate.save(item);
        }

        @Override
        public Optional<OrderItem> findById(com.tmp.order.api.OrderItemId id) {
            return delegate.findById(id);
        }

        @Override
        public List<OrderItem> findByOrderId(OrderId orderId) {
            return delegate.findByOrderId(orderId);
        }
    }

    @SpringBootApplication
    @Import({
        com.tmp.infra.db.DatabaseAutoConfiguration.class,
        com.tmp.core.PlatformCoreAutoConfiguration.class,
        com.tmp.document.DocumentEngineAutoConfiguration.class,
        com.tmp.capability.CapabilityEngineAutoConfiguration.class,
        com.tmp.security.SecurityAutoConfiguration.class,
        OrderManagementAutoConfiguration.class,
        TestItemFaultConfig.class
    })
    static class TestApplication {}

    static class TestItemFaultConfig {
        @Bean
        FailingItemSaveToggle failingItemSaveToggle() {
            return new FailingItemSaveToggle();
        }

        @Bean
        BeanPostProcessor orderItemRepositoryFaultInjector(FailingItemSaveToggle toggle) {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof OrderItemRepository repository
                            && !(bean instanceof ToggleableOrderItemRepository)) {
                        return new ToggleableOrderItemRepository(repository, toggle);
                    }
                    return bean;
                }
            };
        }
    }
}
