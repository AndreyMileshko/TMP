package com.tmp.order.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.core.api.PlatformCore;
import com.tmp.core.api.event.DomainEvent;
import com.tmp.core.api.event.EventSubscription;
import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentStatus;
import com.tmp.order.OrderManagementAutoConfiguration;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderItemStatus;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.event.OrderApproved;
import com.tmp.order.api.event.OrderCancelled;
import com.tmp.order.api.event.OrderCreated;
import com.tmp.order.api.event.OrderItemCancelled;
import com.tmp.order.api.event.OrderItemCreated;
import com.tmp.order.api.event.OrderItemRevisionApproved;
import com.tmp.order.api.event.OrderItemRevisionCreated;
import com.tmp.order.api.event.OrderItemRevisionUpdated;
import com.tmp.order.api.event.OrderItemUpdated;
import com.tmp.order.api.event.OrderUpdated;
import com.tmp.order.application.payload.DocumentId;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.OrderApprovePayload;
import com.tmp.order.application.payload.OrderCancelPayload;
import com.tmp.order.application.payload.OrderCreatePayload;
import com.tmp.order.application.payload.OrderDocumentPayload;
import com.tmp.order.application.payload.OrderDocumentPayloadPort;
import com.tmp.order.application.payload.OrderItemCancelPayload;
import com.tmp.order.application.payload.OrderItemCreatePayload;
import com.tmp.order.application.payload.OrderItemRevisionApprovePayload;
import com.tmp.order.application.payload.OrderItemRevisionCreatePayload;
import com.tmp.order.application.payload.OrderItemRevisionPayloadLine;
import com.tmp.order.application.payload.OrderItemRevisionUpdatePayload;
import com.tmp.order.application.payload.OrderItemUpdatePayload;
import com.tmp.order.application.payload.OrderUpdatePayload;
import com.tmp.order.application.processing.ProcessingOperation;
import com.tmp.order.application.processing.ProcessingRecord;
import com.tmp.order.application.processing.ProcessingRecordPort;
import com.tmp.order.domain.CurrencyCode;
import com.tmp.order.domain.CustomerOrder;
import com.tmp.order.domain.ItemCommercialData;
import com.tmp.order.domain.OrderCommercialData;
import com.tmp.order.domain.OrderDirection;
import com.tmp.order.domain.OrderItem;
import com.tmp.order.domain.OrderNumber;
import com.tmp.order.domain.OrderedQuantity;
import com.tmp.order.domain.ProductCode;
import com.tmp.order.domain.repository.CustomerOrderRepository;
import com.tmp.order.domain.repository.OrderItemRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * STAGE5-044 — Document lifecycle through public Document Engine + concrete processors + PostgreSQL.
 *
 * <p>Successful flows do not invoke processors directly.
 */
@Testcontainers
@SpringBootTest(classes = OrderDocumentLifecycleIT.TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "tmp.security.bootstrap.admin-login=admin",
            "tmp.security.bootstrap.admin-display-name=Administrator",
            "tmp.security.bootstrap.admin-password=bootstrap-secret-value"
        })
class OrderDocumentLifecycleIT {

    private static final Instant NOW = Instant.parse("2026-07-27T08:00:00Z");

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
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private OrderDocumentPayloadPort payloads;
    @Autowired private ProcessingRecordPort processing;
    @Autowired private CustomerOrderRepository orders;
    @Autowired private OrderItemRepository items;
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
    void sequentialBusinessScenarioPostsDocumentsThroughDocumentEngine() {
        DocumentMetadata createMeta = createDraft(DocumentTypeCode.ORDER_CREATE, "create");
        assertEquals(DocumentStatus.DRAFT, createMeta.status());
        OrderNumber orderNumber = OrderNumber.of("LC-SEQ-" + shortId(createMeta.id()));
        payloads.create(
                OrderCreatePayload.create(
                        DocumentId.of(createMeta.id()), orderNumber, commercial("Acme"), NOW));
        assertTrue(payloads.existsByDocumentId(DocumentId.of(createMeta.id())));

        int beforeCreateEvents = delivered.size();
        DocumentMetadata postedCreate = documentEngine.postDocument(createMeta.id());
        assertEquals(DocumentStatus.POSTED, postedCreate.status());
        assertTrue(payloads.existsByDocumentId(DocumentId.of(createMeta.id())));
        ProcessingRecord createRecord = requireProcessing(DocumentId.of(createMeta.id()));
        assertTrue(createRecord.resultReference().isPresent());

        OrderCreated createdEvent =
                assertInstanceOf(
                        OrderCreated.class,
                        findLast(OrderCreated.class, beforeCreateEvents));
        OrderId orderId = createdEvent.orderId();
        CustomerOrder order = orders.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.DRAFT, order.status());
        assertEquals(orderNumber, order.orderNumber());

        DocumentMetadata updateMeta = createDraft(DocumentTypeCode.ORDER_UPDATE, "update");
        payloads.create(
                OrderUpdatePayload.create(
                        DocumentId.of(updateMeta.id()),
                        orderId,
                        commercial("Acme Renamed"),
                        NOW));
        int beforeUpdate = delivered.size();
        documentEngine.postDocument(updateMeta.id());
        assertEquals(
                "Acme Renamed",
                orders.findById(orderId).orElseThrow().commercialData().customerName());
        assertInstanceOf(OrderUpdated.class, findLast(OrderUpdated.class, beforeUpdate));
        requireProcessing(DocumentId.of(updateMeta.id()));

        OrderItemId itemId = OrderItemId.generate();
        DocumentMetadata itemCreateMeta = createDraft(DocumentTypeCode.ORDER_ITEM_CREATE, "item-create");
        payloads.create(
                OrderItemCreatePayload.create(
                        DocumentId.of(itemCreateMeta.id()),
                        orderId,
                        itemId,
                        itemCommercial("Door"),
                        OrderedQuantity.of(2),
                        NOW));
        int beforeItemCreate = delivered.size();
        documentEngine.postDocument(itemCreateMeta.id());
        OrderItem item = items.findById(itemId).orElseThrow();
        assertEquals(OrderItemStatus.DRAFT, item.status());
        assertEquals(RevisionNumber.first(), item.draftRevisionNumber().orElseThrow());
        assertTrue(item.activeRevisionNumber().isEmpty());
        assertInstanceOf(OrderItemCreated.class, findLast(OrderItemCreated.class, beforeItemCreate));
        assertInstanceOf(
                OrderItemRevisionCreated.class,
                findLast(OrderItemRevisionCreated.class, beforeItemCreate));
        requireProcessing(DocumentId.of(itemCreateMeta.id()));

        DocumentMetadata itemUpdateMeta = createDraft(DocumentTypeCode.ORDER_ITEM_UPDATE, "item-update");
        payloads.create(
                OrderItemUpdatePayload.create(
                        DocumentId.of(itemUpdateMeta.id()),
                        itemId,
                        itemCommercial("Door Updated"),
                        NOW));
        int beforeItemUpdate = delivered.size();
        documentEngine.postDocument(itemUpdateMeta.id());
        assertEquals(
                "Door Updated",
                items.findById(itemId).orElseThrow().commercialData().name());
        assertInstanceOf(OrderItemUpdated.class, findLast(OrderItemUpdated.class, beforeItemUpdate));
        requireProcessing(DocumentId.of(itemUpdateMeta.id()));

        DocumentMetadata revUpdateMeta =
                createDraft(DocumentTypeCode.ORDER_ITEM_REVISION_UPDATE, "rev-update");
        payloads.create(
                OrderItemRevisionUpdatePayload.create(
                        DocumentId.of(revUpdateMeta.id()),
                        itemId,
                        RevisionNumber.first(),
                        OrderedQuantity.of(3),
                        List.of(
                                OrderItemRevisionPayloadLine.of(
                                        1,
                                        "M1",
                                        "Glass",
                                        BigDecimal.ONE,
                                        "m2",
                                        BigDecimal.ONE)),
                        NOW));
        int beforeRevUpdate = delivered.size();
        documentEngine.postDocument(revUpdateMeta.id());
        OrderItem afterRevUpdate = items.findById(itemId).orElseThrow();
        assertEquals(
                3,
                afterRevUpdate
                        .draftRevision()
                        .orElseThrow()
                        .orderedQuantity()
                        .value()
                        .intValue());
        assertInstanceOf(
                OrderItemRevisionUpdated.class,
                findLast(OrderItemRevisionUpdated.class, beforeRevUpdate));
        requireProcessing(DocumentId.of(revUpdateMeta.id()));

        DocumentMetadata revApproveMeta =
                createDraft(DocumentTypeCode.ORDER_ITEM_REVISION_APPROVE, "rev-approve");
        payloads.create(
                OrderItemRevisionApprovePayload.create(
                        DocumentId.of(revApproveMeta.id()),
                        itemId,
                        RevisionNumber.first(),
                        NOW));
        int beforeRevApprove = delivered.size();
        documentEngine.postDocument(revApproveMeta.id());
        OrderItem activeItem = items.findById(itemId).orElseThrow();
        assertEquals(OrderItemStatus.ACTIVE, activeItem.status());
        assertEquals(RevisionNumber.first(), activeItem.activeRevisionNumber().orElseThrow());
        assertTrue(activeItem.draftRevisionNumber().isEmpty());
        assertInstanceOf(
                OrderItemRevisionApproved.class,
                findLast(OrderItemRevisionApproved.class, beforeRevApprove));
        requireProcessing(DocumentId.of(revApproveMeta.id()));

        DocumentMetadata approveMeta = createDraft(DocumentTypeCode.ORDER_APPROVE, "approve");
        payloads.create(
                OrderApprovePayload.create(DocumentId.of(approveMeta.id()), orderId, NOW));
        int beforeApprove = delivered.size();
        documentEngine.postDocument(approveMeta.id());
        assertEquals(OrderStatus.APPROVED, orders.findById(orderId).orElseThrow().status());
        assertInstanceOf(OrderApproved.class, findLast(OrderApproved.class, beforeApprove));
        requireProcessing(DocumentId.of(approveMeta.id()));
    }

    @Test
    void orderCancelPostsThroughDocumentEngine() {
        OrderId orderId = createPostedDraftOrder("LC-CANCEL");
        DocumentMetadata cancelMeta = createDraft(DocumentTypeCode.ORDER_CANCEL, "cancel");
        payloads.create(OrderCancelPayload.create(DocumentId.of(cancelMeta.id()), orderId, NOW));
        int before = delivered.size();
        documentEngine.postDocument(cancelMeta.id());
        assertEquals(OrderStatus.CANCELLED, orders.findById(orderId).orElseThrow().status());
        assertInstanceOf(OrderCancelled.class, findLast(OrderCancelled.class, before));
        requireProcessing(DocumentId.of(cancelMeta.id()));
    }

    @Test
    void orderItemCancelPostsThroughDocumentEngine() {
        OrderId orderId = createPostedDraftOrder("LC-ITEM-CANCEL");
        OrderItemId itemId = createPostedDraftItem(orderId);
        DocumentMetadata cancelMeta = createDraft(DocumentTypeCode.ORDER_ITEM_CANCEL, "item-cancel");
        payloads.create(
                OrderItemCancelPayload.create(DocumentId.of(cancelMeta.id()), itemId, NOW));
        int before = delivered.size();
        documentEngine.postDocument(cancelMeta.id());
        assertEquals(OrderItemStatus.CANCELLED, items.findById(itemId).orElseThrow().status());
        assertInstanceOf(OrderItemCancelled.class, findLast(OrderItemCancelled.class, before));
        requireProcessing(DocumentId.of(cancelMeta.id()));
    }

    @Test
    void orderItemRevisionCreatePostsThroughDocumentEngine() {
        OrderId orderId = createPostedDraftOrder("LC-REV-CREATE");
        OrderItemId itemId = createPostedDraftItem(orderId);
        approveFirstRevision(itemId);

        DocumentMetadata createRevMeta =
                createDraft(DocumentTypeCode.ORDER_ITEM_REVISION_CREATE, "rev-create");
        payloads.create(
                OrderItemRevisionCreatePayload.create(
                        DocumentId.of(createRevMeta.id()),
                        itemId,
                        RevisionNumber.of(2),
                        RevisionNumber.first(),
                        NOW));
        int before = delivered.size();
        documentEngine.postDocument(createRevMeta.id());
        OrderItem item = items.findById(itemId).orElseThrow();
        assertEquals(RevisionNumber.first(), item.activeRevisionNumber().orElseThrow());
        assertEquals(RevisionNumber.of(2), item.draftRevisionNumber().orElseThrow());
        assertInstanceOf(
                OrderItemRevisionCreated.class, findLast(OrderItemRevisionCreated.class, before));
        requireProcessing(DocumentId.of(createRevMeta.id()));
    }

    @Test
    void unpostOfPostedDocumentIsRejectedWithoutBusinessSideEffects() {
        OrderId orderId = createPostedDraftOrder("LC-UNPOST");
        DocumentMetadata updateMeta = createDraft(DocumentTypeCode.ORDER_UPDATE, "unpost-target");
        DocumentId documentId = DocumentId.of(updateMeta.id());
        OrderDocumentPayload payload =
                OrderUpdatePayload.create(documentId, orderId, commercial("Before Unpost"), NOW);
        payloads.create(payload);
        documentEngine.postDocument(updateMeta.id());

        CustomerOrder before = orders.findById(orderId).orElseThrow();
        ProcessingRecord beforeRecord = requireProcessing(documentId);
        OrderDocumentPayload beforePayload = payloads.findByDocumentId(documentId).orElseThrow();
        int beforeEvents = delivered.size();
        String beforeCustomer = before.commercialData().customerName();
        long beforeVersion = before.version();

        assertThrows(UnsupportedOperationException.class, () -> documentEngine.unpostDocument(updateMeta.id()));

        assertEquals(DocumentStatus.POSTED, documentEngine.findById(updateMeta.id()).orElseThrow().status());
        CustomerOrder after = orders.findById(orderId).orElseThrow();
        assertEquals(beforeCustomer, after.commercialData().customerName());
        assertEquals(beforeVersion, after.version());
        ProcessingRecord afterRecord = requireProcessing(documentId);
        assertEquals(beforeRecord.resultReference(), afterRecord.resultReference());
        assertEquals(
                beforePayload.identity().payloadRevision(),
                payloads.findByDocumentId(documentId).orElseThrow().identity().payloadRevision());
        assertEquals(beforeEvents, delivered.size());
    }

    @Test
    void closeDoesNotChangeBusinessStateOrRepublishBusinessEvent() {
        OrderId orderId = createPostedDraftOrder("LC-CLOSE");
        DocumentMetadata updateMeta = createDraft(DocumentTypeCode.ORDER_UPDATE, "close-target");
        DocumentId documentId = DocumentId.of(updateMeta.id());
        payloads.create(
                OrderUpdatePayload.create(documentId, orderId, commercial("Close Me"), NOW));
        documentEngine.postDocument(updateMeta.id());

        CustomerOrder before = orders.findById(orderId).orElseThrow();
        ProcessingRecord beforeRecord = requireProcessing(documentId);
        int beforeBusinessEvents = countBusinessEvents();

        DocumentMetadata closed = documentEngine.closeDocument(updateMeta.id());
        assertEquals(DocumentStatus.CLOSED, closed.status());

        CustomerOrder after = orders.findById(orderId).orElseThrow();
        assertEquals(before.status(), after.status());
        assertEquals(before.version(), after.version());
        assertEquals(before.commercialData().customerName(), after.commercialData().customerName());
        assertTrue(payloads.existsByDocumentId(documentId));
        ProcessingRecord afterRecord = requireProcessing(documentId);
        assertEquals(beforeRecord.resultReference(), afterRecord.resultReference());
        assertEquals(beforeBusinessEvents, countBusinessEvents());
    }

    @Test
    void deleteDraftRemovesTypedPayloadAndLeavesAggregateUntouched() {
        OrderId orderId = createPostedDraftOrder("LC-DELETE");
        long versionBefore = orders.findById(orderId).orElseThrow().version();

        DocumentMetadata draftMeta = createDraft(DocumentTypeCode.ORDER_UPDATE, "delete-draft");
        DocumentId documentId = DocumentId.of(draftMeta.id());
        payloads.create(
                OrderUpdatePayload.create(documentId, orderId, commercial("To Delete"), NOW));
        assertTrue(payloads.existsByDocumentId(documentId));
        assertFalse(processing.exists(documentId, ProcessingOperation.POST));
        int beforeEvents = delivered.size();

        documentEngine.deleteDocument(draftMeta.id());

        assertTrue(documentEngine.findById(draftMeta.id()).isEmpty());
        assertFalse(payloads.existsByDocumentId(documentId));
        assertEquals(0, countTypedPayloadRows(documentId));
        assertFalse(processing.exists(documentId, ProcessingOperation.POST));
        assertEquals(versionBefore, orders.findById(orderId).orElseThrow().version());
        assertEquals(OrderStatus.DRAFT, orders.findById(orderId).orElseThrow().status());
        assertEquals(0, countBusinessEventsFrom(beforeEvents));
    }

    @Test
    void deletePostedDocumentIsRejected() {
        DocumentMetadata createMeta = createDraft(DocumentTypeCode.ORDER_CREATE, "delete-posted");
        DocumentId documentId = DocumentId.of(createMeta.id());
        payloads.create(
                OrderCreatePayload.create(
                        documentId,
                        OrderNumber.of("LC-DEL-POSTED-" + shortId(createMeta.id())),
                        commercial("X"),
                        NOW));
        documentEngine.postDocument(createMeta.id());
        assertThrows(IllegalStateException.class, () -> documentEngine.deleteDocument(createMeta.id()));
        assertEquals(DocumentStatus.POSTED, documentEngine.findById(createMeta.id()).orElseThrow().status());
        assertTrue(payloads.existsByDocumentId(documentId));
        requireProcessing(documentId);
    }

    @Test
    void businessEventIsAbsentBeforeCommitAndPresentAfter() {
        DocumentMetadata createMeta = createDraft(DocumentTypeCode.ORDER_CREATE, "event-timing");
        DocumentId documentId = DocumentId.of(createMeta.id());
        payloads.create(
                OrderCreatePayload.create(
                        documentId,
                        OrderNumber.of("LC-EVT-" + shortId(createMeta.id())),
                        commercial("Timing"),
                        NOW));

        List<OrderCreated> observed = new ArrayList<>();
        EventSubscription local =
                platformCore
                        .eventBus()
                        .subscribeDomain(
                                OrderCreated.class, event -> observed.add((OrderCreated) event));
        try {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.executeWithoutResult(
                    status -> {
                        documentEngine.postDocument(createMeta.id());
                        assertTrue(
                                observed.isEmpty(),
                                "OrderCreated must not be delivered before commit");
                    });
            assertEquals(1, observed.size());
            assertEquals(
                    DocumentStatus.POSTED,
                    documentEngine.findById(createMeta.id()).orElseThrow().status());
            assertEquals(observed.get(0).orderId(), orders.findById(observed.get(0).orderId()).orElseThrow().id());
        } finally {
            local.unsubscribe();
        }
    }

    private OrderId createPostedDraftOrder(String prefix) {
        DocumentMetadata createMeta = createDraft(DocumentTypeCode.ORDER_CREATE, prefix);
        DocumentId documentId = DocumentId.of(createMeta.id());
        payloads.create(
                OrderCreatePayload.create(
                        documentId,
                        OrderNumber.of(prefix + "-" + shortId(createMeta.id())),
                        commercial("Seed"),
                        NOW));
        int before = delivered.size();
        documentEngine.postDocument(createMeta.id());
        return assertInstanceOf(OrderCreated.class, findLast(OrderCreated.class, before)).orderId();
    }

    private OrderItemId createPostedDraftItem(OrderId orderId) {
        OrderItemId itemId = OrderItemId.generate();
        DocumentMetadata meta = createDraft(DocumentTypeCode.ORDER_ITEM_CREATE, "seed-item");
        payloads.create(
                OrderItemCreatePayload.create(
                        DocumentId.of(meta.id()),
                        orderId,
                        itemId,
                        itemCommercial("Seed Item"),
                        OrderedQuantity.of(1),
                        NOW));
        documentEngine.postDocument(meta.id());
        return itemId;
    }

    private void approveFirstRevision(OrderItemId itemId) {
        DocumentMetadata revUpdate =
                createDraft(DocumentTypeCode.ORDER_ITEM_REVISION_UPDATE, "seed-rev-update");
        payloads.create(
                OrderItemRevisionUpdatePayload.create(
                        DocumentId.of(revUpdate.id()),
                        itemId,
                        RevisionNumber.first(),
                        OrderedQuantity.of(1),
                        List.of(
                                OrderItemRevisionPayloadLine.of(
                                        1,
                                        "M-SEED",
                                        "Material",
                                        BigDecimal.ONE,
                                        "pcs",
                                        BigDecimal.ZERO)),
                        NOW));
        documentEngine.postDocument(revUpdate.id());

        DocumentMetadata revApprove =
                createDraft(DocumentTypeCode.ORDER_ITEM_REVISION_APPROVE, "seed-rev-approve");
        payloads.create(
                OrderItemRevisionApprovePayload.create(
                        DocumentId.of(revApprove.id()), itemId, RevisionNumber.first(), NOW));
        documentEngine.postDocument(revApprove.id());
    }

    private DocumentMetadata createDraft(DocumentTypeCode type, String title) {
        return documentEngine.createDocument(new CreateDocumentCommand(type.name(), title));
    }

    private ProcessingRecord requireProcessing(DocumentId documentId) {
        assertTrue(processing.exists(documentId, ProcessingOperation.POST));
        return processing
                .findByDocumentIdAndOperation(documentId, ProcessingOperation.POST)
                .orElseThrow();
    }

    private DomainEvent findLast(Class<? extends DomainEvent> type, int fromIndex) {
        for (int i = delivered.size() - 1; i >= fromIndex; i--) {
            DomainEvent event = delivered.get(i);
            if (type.isInstance(event)) {
                return event;
            }
        }
        throw new AssertionError("Expected event " + type.getSimpleName() + " after index " + fromIndex);
    }

    private int countBusinessEvents() {
        return (int)
                delivered.stream()
                        .filter(OrderDocumentLifecycleIT::isOrderBusinessEvent)
                        .count();
    }

    private int countBusinessEventsFrom(int fromIndex) {
        return (int)
                delivered.subList(fromIndex, delivered.size()).stream()
                        .filter(OrderDocumentLifecycleIT::isOrderBusinessEvent)
                        .count();
    }

    private static boolean isOrderBusinessEvent(DomainEvent event) {
        return event instanceof OrderCreated
                || event instanceof OrderUpdated
                || event instanceof OrderApproved
                || event instanceof OrderCancelled
                || event instanceof OrderItemCreated
                || event instanceof OrderItemUpdated
                || event instanceof OrderItemCancelled
                || event instanceof OrderItemRevisionCreated
                || event instanceof OrderItemRevisionUpdated
                || event instanceof OrderItemRevisionApproved;
    }

    private int countTypedPayloadRows(DocumentId documentId) {
        Integer count =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM order_management.order_document_payload
                        WHERE document_id = ?
                        """,
                        Integer.class,
                        documentId.value());
        return count == null ? 0 : count;
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
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

    private static ItemCommercialData itemCommercial(String name) {
        return ItemCommercialData.of(ProductCode.of("P-1"), name, null);
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
