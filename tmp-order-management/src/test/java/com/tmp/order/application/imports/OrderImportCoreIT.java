package com.tmp.order.application.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.tmp.order.api.imports.OrderImportDuplicateException;
import com.tmp.order.api.imports.OrderImportPosition;
import com.tmp.order.api.imports.OrderImportPreview;
import com.tmp.order.api.imports.OrderImportService;
import com.tmp.order.api.imports.OrderImportSpecificationLine;
import com.tmp.order.api.imports.PreparedOrderImportPlan;
import com.tmp.order.application.payload.DocumentId;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.OrderCreatePayload;
import com.tmp.order.application.payload.OrderDocumentPayloadPort;
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
import com.tmp.security.api.UserId;
import com.tmp.security.api.UserSummary;
import java.math.BigDecimal;
import java.time.Instant;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * STAGE5-053 — Import Core PostgreSQL integration: success, preview isolation, atomic rollback,
 * conflict and duplicate protection.
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
    @Autowired private OrderImportMetadataRepository metadataRepository;
    @Autowired private DocumentEngine documentEngine;
    @Autowired private OrderDocumentPayloadPort payloads;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private FailingMetadataToggle failingMetadataToggle;

    private UserId importerUserId;

    @BeforeEach
    void setUp() {
        failingMetadataToggle.failNextSave.set(false);
        failingMetadataToggle.skipNextExistsCheck.set(false);
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
    void confirmCreatesIncompleteDraftStructureThroughDocuments() {
        OrderImportBatch batch = sampleBatch("IMP-OK-1", "checksum-ok-1", "file-a.stxt");
        OrderImportPreview preview = orderImportService.preview(batch);
        assertTrue(preview.canConfirm());
        assertEquals(0, countOrders());
        assertEquals(0, countItems());
        assertEquals(0, countSpecLines());
        assertEquals(0, countMetadata());
        assertEquals(0, countProcessing());

        OrderImportConfirmResult result =
                orderImportService.confirm(preview.preparedPlan().orElseThrow());

        assertEquals("IMP-OK-1", result.orderNumber());
        assertEquals(1, result.createdPositionCount());
        assertEquals(2, result.createdSpecificationLineCount());
        assertNotNull(result.importMetadataId());
        assertEquals(importerUserId.value(), loadImportedBy(result.importMetadataId()));

        CustomerOrder order = orders.findById(result.orderId()).orElseThrow();
        assertEquals(OrderStatus.DRAFT, order.status());
        assertNull(order.commercialData().customerName());
        assertNull(order.commercialData().direction());
        assertNull(order.commercialData().currency());
        assertNull(order.commercialData().contractRef());
        assertNull(order.commercialData().siteRef());

        List<OrderItem> orderItems = items.findByOrderId(order.id());
        assertEquals(1, orderItems.size());
        OrderItem item = orderItems.get(0);
        assertEquals(OrderItemStatus.DRAFT, item.status());
        assertEquals("1", item.commercialData().externalPositionNumber());
        assertNull(item.commercialData().productCode());
        assertNull(item.commercialData().name());
        assertTrue(item.draftRevisionNumber().isPresent());
        assertTrue(item.activeRevisionNumber().isEmpty());

        var revision = item.draftRevision().orElseThrow();
        assertEquals(RevisionStatus.DRAFT, revision.status());
        assertEquals(0, OrderedQuantity.of(8).value().compareTo(revision.orderedQuantity().value()));
        List<SpecificationLine> lines = revision.specification().orElseThrow().lines();
        assertEquals(2, lines.size());
        assertEquals(0, new BigDecimal("16").compareTo(lines.get(0).lineQuantity()));
        assertNull(lines.get(0).color());
        assertNull(lines.get(1).lengthMm());
        assertEquals(OrderImportDefaults.UNIT_OF_MEASURE, lines.get(0).unitOfMeasure());
        assertEquals(1, countMetadata());
        assertTrue(countProcessing() >= 3);
    }

    @Test
    void previewLeavesNoPersistence() {
        OrderImportBatch batch = sampleBatch("IMP-PREV-1", "checksum-prev-1", "prev.stxt");
        orderImportService.preview(batch);
        assertEquals(0, countOrders());
        assertEquals(0, countItems());
        assertEquals(0, countRevisions());
        assertEquals(0, countSpecLines());
        assertEquals(0, countMetadata());
        assertEquals(0, countProcessing());
        assertEquals(0, countPayloads());
    }

    @Test
    void confirmRollbackOnTestOnlyMetadataFailureLeavesNoPartialData() {
        OrderImportBatch batch = sampleBatch("IMP-RB-1", "checksum-rb-1", "rb.stxt");
        PreparedOrderImportPlan plan =
                orderImportService.preview(batch).preparedPlan().orElseThrow();
        int documentsBefore = countDocuments();
        failingMetadataToggle.failNextSave.set(true);

        RuntimeException thrown =
                assertThrows(RuntimeException.class, () -> orderImportService.confirm(plan));
        assertFalse(String.valueOf(thrown.getMessage()).toLowerCase().contains("sql"));
        assertEquals(0, countOrders());
        assertEquals(0, countItems());
        assertEquals(0, countRevisions());
        assertEquals(0, countSpecLines());
        assertEquals(0, countMetadata());
        assertEquals(0, countPayloads());
        assertEquals(0, countProcessing());
        assertEquals(documentsBefore, countDocuments());
    }

    @Test
    void confirmAfterPreviewDuplicateChecksumRaceYieldsControlledDuplicate() {
        OrderImportBatch batch = sampleBatch("IMP-DUP-RACE-1", "checksum-dup-race", "dup-race.stxt");
        PreparedOrderImportPlan plan =
                orderImportService.preview(batch).preparedPlan().orElseThrow();

        OrderId seededOrder = seedExistingOrder("IMP-DUP-RACE-SEED");
        metadataRepository.save(
                OrderImportMetadata.of(
                        UUID.randomUUID(),
                        "STXT",
                        "inserted-after-preview.stxt",
                        "checksum-dup-race",
                        Instant.now(),
                        importerUserId,
                        seededOrder));

        int ordersBefore = countOrders();
        int itemsBefore = countItems();
        int revisionsBefore = countRevisions();
        int linesBefore = countSpecLines();
        int metadataBefore = countMetadata();

        OrderImportDuplicateException duplicate =
                assertThrows(
                        OrderImportDuplicateException.class, () -> orderImportService.confirm(plan));
        assertEquals(OrderImportDuplicateException.USER_MESSAGE, duplicate.getMessage());
        assertFalse(duplicate.getMessage().toLowerCase().contains("sql"));
        assertEquals(ordersBefore, countOrders());
        assertEquals(itemsBefore, countItems());
        assertEquals(revisionsBefore, countRevisions());
        assertEquals(linesBefore, countSpecLines());
        assertEquals(metadataBefore, countMetadata());
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
        int metadataBefore = countMetadata();

        OrderImportConflictException conflict =
                assertThrows(
                        OrderImportConflictException.class, () -> orderImportService.confirm(plan));
        assertEquals(OrderImportConflictException.USER_MESSAGE, conflict.getMessage());
        assertFalse(conflict.getMessage().toLowerCase().contains("sql"));
        assertEquals(
                originalCustomer,
                orders.findById(existingId).orElseThrow().commercialData().customerName());
        assertEquals(itemsBefore, countItems());
        assertEquals(metadataBefore, countMetadata());
        assertEquals(1, countOrders());
    }

    @Test
    void secondConfirmOfSameChecksumMapsUniqueViolationToControlledDuplicate() {
        OrderImportBatch batchA = sampleBatch("IMP-UC-1", "checksum-uc-1", "uc-a.stxt");
        OrderImportBatch batchB = sampleBatch("IMP-UC-2", "checksum-uc-1", "uc-b.stxt");
        PreparedOrderImportPlan planA =
                orderImportService.preview(batchA).preparedPlan().orElseThrow();
        PreparedOrderImportPlan planB =
                orderImportService.preview(batchB).preparedPlan().orElseThrow();

        orderImportService.confirm(planA);
        assertEquals(1, countOrders());
        assertEquals(1, countMetadata());

        // Simulate TOCTOU: pre-check misses existing checksum, unique constraint fires on save.
        failingMetadataToggle.skipNextExistsCheck.set(true);
        OrderImportDuplicateException duplicate =
                assertThrows(
                        OrderImportDuplicateException.class, () -> orderImportService.confirm(planB));
        assertEquals(OrderImportDuplicateException.USER_MESSAGE, duplicate.getMessage());
        assertFalse(
                duplicate.getCause()
                        instanceof org.springframework.dao.DataIntegrityViolationException);
        assertEquals(1, countOrders());
        assertEquals(1, countMetadata());
        assertEquals(1, countItems());
        assertEquals(0, countOrdersWithNumber("IMP-UC-2"));
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
        assertEquals(0, countMetadata());
    }

    @Test
    void duplicateChecksumIsRejectedEvenWithDifferentSourceReference() {
        OrderImportBatch first = sampleBatch("IMP-DUP-1", "checksum-dup-1", "first.stxt");
        orderImportService.confirm(orderImportService.preview(first).preparedPlan().orElseThrow());
        assertEquals(1, countOrders());

        OrderImportBatch second = sampleBatch("IMP-DUP-2", "checksum-dup-1", "second.stxt");
        OrderImportDuplicateException duplicate =
                assertThrows(
                        OrderImportDuplicateException.class,
                        () -> orderImportService.preview(second));
        assertEquals(OrderImportDuplicateException.USER_MESSAGE, duplicate.getMessage());
        assertEquals(1, countOrders());
        assertEquals(1, countMetadata());
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
    void metadataUniqueConstraintRaceIsEnforcedAndMappedOnConfirm() {
        OrderImportBatch batch = sampleBatch("IMP-RACE-1", "checksum-race-1", "race.stxt");
        OrderImportConfirmResult first =
                orderImportService.confirm(
                        orderImportService.preview(batch).preparedPlan().orElseThrow());

        assertThrows(
                DuplicateKeyException.class,
                () ->
                        metadataRepository.save(
                                OrderImportMetadata.of(
                                        UUID.randomUUID(),
                                        "STXT",
                                        "other-name.stxt",
                                        "checksum-race-1",
                                        Instant.now(),
                                        importerUserId,
                                        first.orderId())));

        OrderImportBatch retry = sampleBatch("IMP-RACE-2", "checksum-race-1", "retry.stxt");
        OrderImportDuplicateException duplicate =
                assertThrows(
                        OrderImportDuplicateException.class,
                        () -> orderImportService.preview(retry));
        assertEquals(OrderImportDuplicateException.USER_MESSAGE, duplicate.getMessage());
        assertFalse(duplicate.getMessage().toLowerCase().contains("sql"));
    }

    private void ensureImporter() {
        authenticationService.login(Login.of("admin"), "bootstrap-secret-value".toCharArray());
        Optional<UserSummary> existing =
                userAdministrationService.listUsers(0, 100, null).stream()
                        .filter(user -> "importer".equalsIgnoreCase(user.login().value()))
                        .findFirst();
        UserSummary importer =
                existing.orElseGet(
                        () ->
                                userAdministrationService.createUser(
                                        Login.of("importer"),
                                        DisplayName.of("Importer"),
                                        IMPORTER_PASSWORD.clone()));
        importerUserId = importer.id();
        roleAdministrationService.grantIndividualPermission(
                importer.id(), OrderManagementPermissions.ORDER_CREATE);
        roleAdministrationService.grantIndividualPermission(
                importer.id(), OrderManagementPermissions.ITEM_CREATE);
        roleAdministrationService.grantIndividualPermission(
                importer.id(), OrderManagementPermissions.REVISION_EDIT);
        authenticationService.logout();
        authenticationService.login(Login.of("importer"), IMPORTER_PASSWORD.clone());
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
                List.of(
                        OrderImportPosition.of(
                                "1",
                                8,
                                List.of(
                                        OrderImportSpecificationLine.of(
                                                "107.225",
                                                "Штапик",
                                                null,
                                                new BigDecimal("2066"),
                                                new BigDecimal("16")),
                                        OrderImportSpecificationLine.of(
                                                "200.1",
                                                "Профиль",
                                                " ",
                                                null,
                                                new BigDecimal("4"))))));
    }

    private int countOrders() {
        return count("SELECT COUNT(*) FROM order_management.orders");
    }

    private int countOrdersWithNumber(String orderNumber) {
        Integer value =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM order_management.orders WHERE order_number = ?",
                        Integer.class,
                        orderNumber);
        return value == null ? 0 : value;
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

    private int countMetadata() {
        return count("SELECT COUNT(*) FROM order_management.order_import_metadata");
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

    private UUID loadImportedBy(UUID importId) {
        return jdbc.queryForObject(
                """
                SELECT imported_by FROM order_management.order_import_metadata
                WHERE import_id = ?
                """,
                UUID.class,
                importId);
    }

    static final class FailingMetadataToggle {
        final AtomicBoolean failNextSave = new AtomicBoolean(false);
        final AtomicBoolean skipNextExistsCheck = new AtomicBoolean(false);
    }

    static final class ToggleableOrderImportMetadataRepository
            implements OrderImportMetadataRepository {

        private final OrderImportMetadataRepository delegate;
        private final FailingMetadataToggle toggle;

        ToggleableOrderImportMetadataRepository(
                OrderImportMetadataRepository delegate, FailingMetadataToggle toggle) {
            this.delegate = delegate;
            this.toggle = toggle;
        }

        @Override
        public boolean existsBySourceTypeAndChecksum(String sourceType, String contentChecksum) {
            if (toggle.skipNextExistsCheck.compareAndSet(true, false)) {
                return false;
            }
            return delegate.existsBySourceTypeAndChecksum(sourceType, contentChecksum);
        }

        @Override
        public Optional<OrderImportMetadata> findBySourceTypeAndChecksum(
                String sourceType, String contentChecksum) {
            return delegate.findBySourceTypeAndChecksum(sourceType, contentChecksum);
        }

        @Override
        public OrderImportMetadata save(OrderImportMetadata metadata) {
            if (toggle.failNextSave.compareAndSet(true, false)) {
                throw new IllegalStateException("test-only metadata failure");
            }
            return delegate.save(metadata);
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
        TestMetadataConfig.class
    })
    static class TestApplication {}

    static class TestMetadataConfig {
        @Bean
        FailingMetadataToggle failingMetadataToggle() {
            return new FailingMetadataToggle();
        }

        @Bean
        BeanPostProcessor importMetadataRepositoryFaultInjector(FailingMetadataToggle toggle) {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof OrderImportMetadataRepository repository
                            && !(bean instanceof ToggleableOrderImportMetadataRepository)) {
                        return new ToggleableOrderImportMetadataRepository(repository, toggle);
                    }
                    return bean;
                }
            };
        }
    }
}
