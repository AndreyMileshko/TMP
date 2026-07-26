package com.tmp.order.application.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.core.api.event.DomainEvent;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentStatus;
import com.tmp.document.api.TransactionalEventPublisher;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.event.OrderApproved;
import com.tmp.order.application.order.ApproveOrderUseCase;
import com.tmp.order.application.order.InMemoryCustomerOrderRepository;
import com.tmp.order.application.order.InMemoryOrderItemRepository;
import com.tmp.order.application.order.OrderApprovalRejectedException;
import com.tmp.order.application.payload.DocumentId;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.InMemoryOrderDocumentPayloadPort;
import com.tmp.order.application.payload.OrderApprovePayload;
import com.tmp.order.application.payload.OrderDocumentPayloadPort;
import com.tmp.order.application.processing.InMemoryProcessingRecordPort;
import com.tmp.order.application.processing.ProcessingOperation;
import com.tmp.order.application.processing.ProcessingRecordPort;
import com.tmp.order.domain.CurrencyCode;
import com.tmp.order.domain.CustomerOrder;
import com.tmp.order.domain.InvalidOrderStateException;
import com.tmp.order.domain.ItemCommercialData;
import com.tmp.order.domain.ItemSpecification;
import com.tmp.order.domain.OrderCommercialData;
import com.tmp.order.domain.OrderDirection;
import com.tmp.order.domain.OrderItem;
import com.tmp.order.domain.OrderNumber;
import com.tmp.order.domain.OrderedQuantity;
import com.tmp.order.domain.ProductCode;
import com.tmp.order.domain.SpecificationLine;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderApproveDocumentProcessorTest {

    private static final Instant NOW = Instant.parse("2026-07-26T14:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private OrderDocumentPayloadPort payloads;
    private ProcessingRecordPort processing;
    private InMemoryCustomerOrderRepository orders;
    private InMemoryOrderItemRepository items;
    private List<DomainEvent> published;
    private OrderApproveDocumentProcessor processor;

    @BeforeEach
    void setUp() {
        payloads = new InMemoryOrderDocumentPayloadPort();
        processing = new InMemoryProcessingRecordPort();
        orders = new InMemoryCustomerOrderRepository();
        items = new InMemoryOrderItemRepository();
        published = new ArrayList<>();
        TransactionalEventPublisher publisher = published::add;
        processor =
                new OrderApproveDocumentProcessor(
                        payloads,
                        processing,
                        publisher,
                        new ApproveOrderUseCase(orders, items, CLOCK),
                        CLOCK);
    }

    @Test
    void draftOrderWithActiveItemBecomesApproved() {
        CustomerOrder draft = seedDraft("ORD-APPR-1");
        seedActiveItem(draft.id());
        DocumentId documentId = DocumentId.generate();
        payloads.create(OrderApprovePayload.create(documentId, draft.id(), NOW));

        processor.onPost(context(documentId));

        assertEquals(OrderStatus.APPROVED, orders.findById(draft.id()).orElseThrow().status());
        assertEquals(1, published.size());
        assertEquals(draft.id(), assertInstanceOf(OrderApproved.class, published.get(0)).orderId());
        assertTrue(processing.exists(documentId, ProcessingOperation.POST));
    }

    @Test
    void orderWithoutActiveItemsIsRejected() {
        CustomerOrder draft = seedDraft("ORD-APPR-NONE");
        seedDraftItem(draft.id());
        DocumentId documentId = DocumentId.generate();
        payloads.create(OrderApprovePayload.create(documentId, draft.id(), NOW));

        assertThrows(
                OrderApprovalRejectedException.class, () -> processor.onPost(context(documentId)));
        assertEquals(OrderStatus.DRAFT, orders.findById(draft.id()).orElseThrow().status());
        assertTrue(published.isEmpty());
        assertTrue(!processing.exists(documentId, ProcessingOperation.POST));
    }

    @Test
    void reApprovalIsRejectedByDomainWithoutProcessingOrEvent() {
        CustomerOrder draft = seedDraft("ORD-APPR-RE");
        seedActiveItem(draft.id());
        CustomerOrder alreadyApproved = draft.approve(CLOCK);
        orders.save(alreadyApproved);

        DocumentId documentId = DocumentId.generate();
        payloads.create(OrderApprovePayload.create(documentId, draft.id(), NOW));

        assertThrows(InvalidOrderStateException.class, () -> processor.onPost(context(documentId)));
        assertTrue(published.isEmpty());
        assertTrue(!processing.exists(documentId, ProcessingOperation.POST));
        assertEquals(
                OrderStatus.APPROVED, orders.findById(draft.id()).orElseThrow().status());
    }

    private CustomerOrder seedDraft(String number) {
        return orders.save(
                CustomerOrder.create(
                        OrderId.generate(),
                        OrderNumber.of(number),
                        OrderCommercialData.of(
                                "C-1",
                                "Customer",
                                null,
                                null,
                                null,
                                OrderDirection.PRIVATE,
                                CurrencyCode.of("USD")),
                        CLOCK));
    }

    private void seedDraftItem(OrderId orderId) {
        items.save(
                OrderItem.create(
                        OrderItemId.generate(),
                        orderId,
                        ItemCommercialData.of(ProductCode.of("P-1"), "Item", null),
                        OrderedQuantity.of(1),
                        CLOCK));
    }

    private void seedActiveItem(OrderId orderId) {
        OrderItem draftItem =
                OrderItem.create(
                        OrderItemId.generate(),
                        orderId,
                        ItemCommercialData.of(ProductCode.of("P-1"), "Item", null),
                        OrderedQuantity.of(1),
                        CLOCK);
        ItemSpecification spec =
                ItemSpecification.of(
                        draftItem.id(),
                        RevisionNumber.first(),
                        List.of(
                                SpecificationLine.of(
                                        "MAT-1",
                                        "Glass",
                                        BigDecimal.ONE,
                                        "m2",
                                        BigDecimal.valueOf(1.2))));
        items.save(draftItem.updateDraftSpecification(spec, CLOCK).approveDraftRevision(CLOCK));
    }

    private static DocumentOperationContext context(DocumentId documentId) {
        DocumentMetadata metadata =
                new DocumentMetadata(
                        documentId.value(),
                        DocumentTypeCode.ORDER_APPROVE.name(),
                        "DOC-" + documentId,
                        "title",
                        DocumentStatus.DRAFT,
                        0L,
                        NOW,
                        NOW,
                        null,
                        null);
        return () -> metadata;
    }
}
