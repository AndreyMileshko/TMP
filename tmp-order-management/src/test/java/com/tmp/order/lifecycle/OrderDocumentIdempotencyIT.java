package com.tmp.order.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.core.api.PlatformCore;
import com.tmp.core.api.event.DomainEvent;
import com.tmp.core.api.event.EventSubscription;
import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentStatus;
import com.tmp.order.OrderManagementAutoConfiguration;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.event.OrderCreated;
import com.tmp.order.application.document.OrderCreateDocumentProcessor;
import com.tmp.order.application.payload.DocumentId;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.OrderCreatePayload;
import com.tmp.order.application.payload.OrderDocumentPayloadPort;
import com.tmp.order.application.processing.DuplicateProcessingRecordException;
import com.tmp.order.application.processing.ProcessingOperation;
import com.tmp.order.application.processing.ProcessingRecord;
import com.tmp.order.application.processing.ProcessingRecordPort;
import com.tmp.order.application.processing.ResultReference;
import com.tmp.order.domain.CurrencyCode;
import com.tmp.order.domain.CustomerOrder;
import com.tmp.order.domain.OrderCommercialData;
import com.tmp.order.domain.OrderDirection;
import com.tmp.order.domain.OrderNumber;
import com.tmp.order.domain.PayloadRevision;
import com.tmp.order.domain.repository.CustomerOrderRepository;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
 * STAGE5-045 — Idempotency: public repeated post rejection + internal processor guard + concurrent
 * processing-record uniqueness.
 */
@Testcontainers
@SpringBootTest(classes = OrderDocumentIdempotencyIT.TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "tmp.security.bootstrap.admin-login=admin",
            "tmp.security.bootstrap.admin-display-name=Administrator",
            "tmp.security.bootstrap.admin-password=bootstrap-secret-value"
        })
class OrderDocumentIdempotencyIT {

    private static final Instant NOW = Instant.parse("2026-07-27T09:00:00Z");

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
    @Autowired private OrderDocumentPayloadPort payloads;
    @Autowired private ProcessingRecordPort processing;
    @Autowired private CustomerOrderRepository orders;
    @Autowired private OrderCreateDocumentProcessor orderCreateProcessor;
    @Autowired private JdbcTemplate jdbc;

    private final List<DomainEvent> delivered = new CopyOnWriteArrayList<>();
    private EventSubscription subscription;

    @BeforeEach
    void setUp() {
        if (subscription != null) {
            subscription.unsubscribe();
        }
        delivered.clear();
        subscription =
                platformCore
                        .eventBus()
                        .subscribeDomain(DomainEvent.class, event -> delivered.add(event));

        jdbc.update("DELETE FROM order_management.order_document_processing");
        jdbc.update("DELETE FROM order_management.order_document_payload");
        jdbc.update("DELETE FROM order_management.item_specification_lines");
        jdbc.update("DELETE FROM order_management.item_specifications");
        jdbc.update("DELETE FROM order_management.order_item_revisions");
        jdbc.update("DELETE FROM order_management.order_items");
        jdbc.update("DELETE FROM order_management.orders");
    }

    @Test
    void publicRepeatedPostIsRejectedByLifecycleWithoutBusinessSideEffects() {
        PostedOrder posted = postOrderCreate("IDEM-PUBLIC");

        int eventsBefore = countOrderCreated();
        long versionBefore = orders.findById(posted.orderId()).orElseThrow().version();
        ProcessingRecord recordBefore = requireProcessing(posted.documentId());
        ResultReference resultBefore = recordBefore.resultReference().orElseThrow();

        assertThrows(
                IllegalStateException.class,
                () -> documentEngine.postDocument(posted.metadata().id()));

        assertEquals(
                DocumentStatus.POSTED,
                documentEngine.findById(posted.metadata().id()).orElseThrow().status());
        CustomerOrder after = orders.findById(posted.orderId()).orElseThrow();
        assertEquals(OrderStatus.DRAFT, after.status());
        assertEquals(versionBefore, after.version());
        assertEquals(eventsBefore, countOrderCreated());

        ProcessingRecord recordAfter = requireProcessing(posted.documentId());
        assertEquals(resultBefore, recordAfter.resultReference().orElseThrow());
        assertEquals(
                1,
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM order_management.order_document_processing
                        WHERE document_id = ? AND operation = ?
                        """,
                        Integer.class,
                        posted.documentId().value(),
                        ProcessingOperation.POST.name()));
    }

    @Test
    void internalProcessorGuardSkipsBusinessWhenProcessingRecordExists() throws Exception {
        PostedOrder posted = postOrderCreate("IDEM-INTERNAL");
        ProcessingRecord existing = requireProcessing(posted.documentId());
        ResultReference existingResult = existing.resultReference().orElseThrow();
        long versionBefore = orders.findById(posted.orderId()).orElseThrow().version();
        int eventsBefore = countOrderCreated();

        Method onPost =
                OrderCreateDocumentProcessor.class.getMethod(
                        "onPost", DocumentOperationContext.class);
        assertEquals(void.class, onPost.getReturnType());

        DocumentOperationContext context = contextFor(posted.metadata());
        orderCreateProcessor.onPost(context);
        orderCreateProcessor.onPost(context);

        assertEquals(versionBefore, orders.findById(posted.orderId()).orElseThrow().version());
        assertEquals(eventsBefore, countOrderCreated());
        ProcessingRecord after = requireProcessing(posted.documentId());
        assertEquals(existingResult, after.resultReference().orElseThrow());
        assertEquals(
                1,
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM order_management.order_document_processing
                        WHERE document_id = ?
                        """,
                        Integer.class,
                        posted.documentId().value()));
    }

    @Test
    void concurrentProcessingRecordInsertKeepsSingleRow() throws Exception {
        DocumentId documentId = DocumentId.generate();
        ProcessingRecord record =
                ProcessingRecord.completedPost(
                        documentId,
                        DocumentTypeCode.ORDER_CREATE,
                        PayloadRevision.initial(),
                        NOW,
                        ResultReference.of("order:" + UUID.randomUUID()));

        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger duplicates = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            futures.add(
                    executor.submit(
                            () -> {
                                start.await(5, TimeUnit.SECONDS);
                                try {
                                    processing.insert(record);
                                    successes.incrementAndGet();
                                } catch (DuplicateProcessingRecordException ignored) {
                                    duplicates.incrementAndGet();
                                }
                                return null;
                            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertEquals(1, successes.get());
        assertEquals(3, duplicates.get());
        assertEquals(
                1,
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM order_management.order_document_processing
                        WHERE document_id = ?
                        """,
                        Integer.class,
                        documentId.value()));
    }

    @Test
    void concurrentPublicPostProducesSingleSuccess() throws Exception {
        DocumentMetadata created =
                documentEngine.createDocument(
                        new CreateDocumentCommand(DocumentTypeCode.ORDER_CREATE.name(), "concurrent-post"));
        DocumentId documentId = DocumentId.of(created.id());
        payloads.create(
                OrderCreatePayload.create(
                        documentId,
                        OrderNumber.of("IDEM-CONC-" + created.id().toString().substring(0, 8)),
                        commercial("Concurrent"),
                        NOW));

        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            futures.add(
                    executor.submit(
                            () -> {
                                start.await(5, TimeUnit.SECONDS);
                                try {
                                    documentEngine.postDocument(created.id());
                                    successes.incrementAndGet();
                                } catch (RuntimeException ignored) {
                                    // expected state / optimistic conflicts
                                }
                                return null;
                            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(15, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertEquals(1, successes.get());
        assertEquals(
                DocumentStatus.POSTED, documentEngine.findById(created.id()).orElseThrow().status());
        assertEquals(1, countOrderCreated());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM order_management.orders", Integer.class));
        assertEquals(
                1,
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM order_management.order_document_processing
                        WHERE document_id = ?
                        """,
                        Integer.class,
                        documentId.value()));
    }

    private PostedOrder postOrderCreate(String prefix) {
        DocumentMetadata created =
                documentEngine.createDocument(
                        new CreateDocumentCommand(DocumentTypeCode.ORDER_CREATE.name(), prefix));
        DocumentId documentId = DocumentId.of(created.id());
        payloads.create(
                OrderCreatePayload.create(
                        documentId,
                        OrderNumber.of(prefix + "-" + created.id().toString().substring(0, 8)),
                        commercial("Seed"),
                        NOW));
        int before = countOrderCreated();
        DocumentMetadata posted = documentEngine.postDocument(created.id());
        OrderCreated event = findLastOrderCreated(before);
        return new PostedOrder(posted, documentId, event.orderId());
    }

    private ProcessingRecord requireProcessing(DocumentId documentId) {
        assertTrue(processing.exists(documentId, ProcessingOperation.POST));
        return processing
                .findByDocumentIdAndOperation(documentId, ProcessingOperation.POST)
                .orElseThrow();
    }

    private int countOrderCreated() {
        return (int) delivered.stream().filter(OrderCreated.class::isInstance).count();
    }

    private OrderCreated findLastOrderCreated(int fromIndex) {
        for (int i = delivered.size() - 1; i >= fromIndex; i--) {
            if (delivered.get(i) instanceof OrderCreated created) {
                return created;
            }
        }
        throw new AssertionError("OrderCreated not found");
    }

    private static DocumentOperationContext contextFor(DocumentMetadata metadata) {
        DocumentMetadata draftView =
                new DocumentMetadata(
                        metadata.id(),
                        metadata.documentTypeId(),
                        metadata.documentNumber(),
                        metadata.title(),
                        DocumentStatus.DRAFT,
                        metadata.version(),
                        metadata.createdAt(),
                        metadata.updatedAt(),
                        null,
                        null);
        return () -> draftView;
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

    private record PostedOrder(DocumentMetadata metadata, DocumentId documentId, OrderId orderId) {}

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
