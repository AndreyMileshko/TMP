package com.tmp.order.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.core.api.PlatformCore;
import com.tmp.core.api.event.EventSubscription;
import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentProcessor;
import com.tmp.document.api.DocumentStatus;
import com.tmp.document.api.LifecycleEventType;
import com.tmp.document.api.TransactionalEventPublisher;
import com.tmp.document.api.port.LifecycleJournalPort;
import com.tmp.order.OrderManagementAutoConfiguration;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.event.OrderCreated;
import com.tmp.order.application.order.CreateOrderCommand;
import com.tmp.order.application.order.CreateOrderUseCase;
import com.tmp.order.application.payload.DocumentId;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.OrderCreatePayload;
import com.tmp.order.application.payload.OrderDocumentPayloadPort;
import com.tmp.order.application.processing.ProcessingOperation;
import com.tmp.order.application.processing.ProcessingRecord;
import com.tmp.order.application.processing.ProcessingRecordPort;
import com.tmp.order.application.processing.ResultReference;
import com.tmp.order.domain.CurrencyCode;
import com.tmp.order.domain.CustomerOrder;
import com.tmp.order.domain.OrderCommercialData;
import com.tmp.order.domain.OrderDirection;
import com.tmp.order.domain.OrderNumber;
import com.tmp.order.domain.repository.CustomerOrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
 * STAGE5-046 — Transaction rollback atomicity for Document Engine post boundary using a test-only
 * processor registered through the public API (no production processor mutation).
 */
@Testcontainers
@SpringBootTest(classes = OrderDocumentRollbackIT.TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "tmp.security.bootstrap.admin-login=admin",
            "tmp.security.bootstrap.admin-display-name=Administrator",
            "tmp.security.bootstrap.admin-password=bootstrap-secret-value"
        })
class OrderDocumentRollbackIT {

    private static final Instant NOW = Instant.parse("2026-07-27T10:00:00Z");

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

    @Autowired private DocumentEngine documentEngine;
    @Autowired private PlatformCore platformCore;
    @Autowired
    @Qualifier("transactionalEventPublisher")
    private TransactionalEventPublisher eventPublisher;
    @Autowired private LifecycleJournalPort lifecycleJournal;
    @Autowired private OrderDocumentPayloadPort payloads;
    @Autowired private ProcessingRecordPort processing;
    @Autowired private CustomerOrderRepository orders;
    @Autowired private CreateOrderUseCase createOrderUseCase;
    @Autowired private Clock clock;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void cleanOrderTables() {
        jdbc.update("DELETE FROM order_management.order_document_processing");
        jdbc.update("DELETE FROM order_management.order_document_payload");
        jdbc.update("DELETE FROM order_management.item_specification_lines");
        jdbc.update("DELETE FROM order_management.item_specifications");
        jdbc.update("DELETE FROM order_management.order_item_revisions");
        jdbc.update("DELETE FROM order_management.order_items");
        jdbc.update("DELETE FROM order_management.orders");
    }

    @Test
    void postFailureRollsBackAggregateProcessingJournalAndEvent_thenRetrySucceeds() {
        String typeId = "order.rollback." + UUID.randomUUID();
        AtomicBoolean failOnPost = new AtomicBoolean(true);
        AtomicReference<OrderId> createdOrderId = new AtomicReference<>();
        FaultInjectingProcessor processor =
                new FaultInjectingProcessor(
                        typeId,
                        failOnPost,
                        createdOrderId,
                        createOrderUseCase,
                        processing,
                        eventPublisher,
                        clock);
        documentEngine.registerProcessor(processor);

        DocumentMetadata draft =
                documentEngine.createDocument(new CreateDocumentCommand(typeId, "rollback-post"));
        DocumentId documentId = DocumentId.of(draft.id());
        OrderNumber orderNumber = OrderNumber.of("RB-" + draft.id().toString().substring(0, 8));
        payloads.create(
                OrderCreatePayload.create(documentId, orderNumber, commercial("Rollback"), NOW));
        assertTrue(payloads.existsByDocumentId(documentId));

        List<OrderCreated> delivered = new ArrayList<>();
        EventSubscription subscription =
                platformCore
                        .eventBus()
                        .subscribeDomain(
                                OrderCreated.class, event -> delivered.add((OrderCreated) event));
        try {
            assertThrows(
                    IllegalStateException.class, () -> documentEngine.postDocument(draft.id()));

            assertEquals(
                    DocumentStatus.DRAFT,
                    documentEngine.findById(draft.id()).orElseThrow().status());
            assertTrue(orders.findById(createdOrderId.get()).isEmpty());
            assertFalse(processing.exists(documentId, ProcessingOperation.POST));
            assertTrue(payloads.existsByDocumentId(documentId));
            assertEquals(
                    DocumentTypeCode.ORDER_CREATE,
                    payloads.findByDocumentId(documentId).orElseThrow().documentTypeCode());
            assertTrue(delivered.isEmpty());
            assertEquals(
                    0,
                    lifecycleJournal.findByDocumentId(draft.id()).stream()
                            .filter(entry -> entry.eventType() == LifecycleEventType.POSTED)
                            .count());

            failOnPost.set(false);
            DocumentMetadata posted = documentEngine.postDocument(draft.id());
            assertEquals(DocumentStatus.POSTED, posted.status());
            CustomerOrder saved = orders.findById(createdOrderId.get()).orElseThrow();
            assertEquals(OrderStatus.DRAFT, saved.status());
            assertEquals(orderNumber, saved.orderNumber());
            assertTrue(processing.exists(documentId, ProcessingOperation.POST));
            assertEquals(1, delivered.size());
            assertEquals(saved.id(), delivered.get(0).orderId());
            assertEquals(
                    1,
                    lifecycleJournal.findByDocumentId(draft.id()).stream()
                            .filter(entry -> entry.eventType() == LifecycleEventType.POSTED)
                            .count());
        } finally {
            subscription.unsubscribe();
        }
    }

    private static OrderCommercialData commercial(String customerName) {
        return OrderCommercialData.of(
                "CUST-1",
                customerName,
                "CTR-1",
                "SITE-1",
                "Manager",
                OrderDirection.CORPORATE,
                CurrencyCode.of("EUR"));
    }

    /**
     * Test-only processor: mutates aggregate, writes processing record, schedules after-commit
     * event, then optionally fails so Document Engine transaction rolls back.
     */
    private static final class FaultInjectingProcessor implements DocumentProcessor {

        private final String typeId;
        private final AtomicBoolean failOnPost;
        private final AtomicReference<OrderId> createdOrderId;
        private final CreateOrderUseCase createOrderUseCase;
        private final ProcessingRecordPort processing;
        private final TransactionalEventPublisher eventPublisher;
        private final Clock clock;

        private FaultInjectingProcessor(
                String typeId,
                AtomicBoolean failOnPost,
                AtomicReference<OrderId> createdOrderId,
                CreateOrderUseCase createOrderUseCase,
                ProcessingRecordPort processing,
                TransactionalEventPublisher eventPublisher,
                Clock clock) {
            this.typeId = typeId;
            this.failOnPost = failOnPost;
            this.createdOrderId = createdOrderId;
            this.createOrderUseCase = createOrderUseCase;
            this.processing = processing;
            this.eventPublisher = eventPublisher;
            this.clock = clock;
        }

        @Override
        public String documentTypeId() {
            return typeId;
        }

        @Override
        public void validateCreate(DocumentOperationContext context) {}

        @Override
        public void validateUpdate(DocumentOperationContext context) {}

        @Override
        public void onPost(DocumentOperationContext context) {
            DocumentId documentId = DocumentId.of(context.document().id());
            OrderNumber orderNumber =
                    OrderNumber.of("RB-" + context.document().id().toString().substring(0, 8));
            CustomerOrder order =
                    createOrderUseCase.execute(
                            new CreateOrderCommand(orderNumber, commercial("Rollback")));
            createdOrderId.set(order.id());
            processing.insert(
                    ProcessingRecord.completedPost(
                            documentId,
                            DocumentTypeCode.ORDER_CREATE,
                            com.tmp.order.domain.PayloadRevision.initial(),
                            clock.instant(),
                            ResultReference.of("order:" + order.id().value())));
            eventPublisher.publishAfterCommit(
                    new OrderCreated(order.id(), context.document().id().toString()));
            if (failOnPost.get()) {
                throw new IllegalStateException("Controlled fault injection before commit");
            }
        }

        @Override
        public void onUnpost(DocumentOperationContext context) {
            throw new UnsupportedOperationException("UNPOST IS NOT SUPPORTED");
        }

        @Override
        public void onClose(DocumentOperationContext context) {}

        @Override
        public void onDelete(DocumentOperationContext context) {}
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
