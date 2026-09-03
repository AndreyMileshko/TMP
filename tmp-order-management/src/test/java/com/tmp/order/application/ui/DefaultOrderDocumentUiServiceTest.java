package com.tmp.order.application.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentStatus;
import com.tmp.order.api.OrderDto;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.ui.OrderHeaderDraft;
import com.tmp.order.application.payload.DocumentId;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.DraftPayloadApplicationService;
import com.tmp.order.application.payload.OrderActivatePayload;
import com.tmp.order.application.payload.OrderApprovePayload;
import com.tmp.order.application.payload.OrderCreatePayload;
import com.tmp.order.application.payload.OrderDocumentPayload;
import com.tmp.order.application.processing.ProcessingOperation;
import com.tmp.order.application.processing.ProcessingRecord;
import com.tmp.order.application.processing.ProcessingRecordPort;
import com.tmp.order.application.processing.ResultReference;
import com.tmp.order.capability.OrderManagementPermissions;
import com.tmp.order.domain.CurrencyCode;
import com.tmp.order.domain.OrderCommercialData;
import com.tmp.order.domain.OrderDirection;
import com.tmp.order.domain.OrderNumber;
import com.tmp.order.domain.PayloadRevision;
import com.tmp.security.api.AuthorizationService;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DefaultOrderDocumentUiServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-27T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private DocumentEngine documentEngine;
    private DraftPayloadApplicationService draftPayloads;
    private OrderQueryService orderQueryService;
    private ProcessingRecordPort processingRecords;
    private AuthorizationService authorization;
    private DefaultOrderDocumentUiService service;

    @BeforeEach
    void setUp() {
        documentEngine = mock(DocumentEngine.class);
        draftPayloads = mock(DraftPayloadApplicationService.class);
        orderQueryService = mock(OrderQueryService.class);
        processingRecords = mock(ProcessingRecordPort.class);
        authorization = mock(AuthorizationService.class);
        service =
                new DefaultOrderDocumentUiService(
                        documentEngine,
                        draftPayloads,
                        orderQueryService,
                        processingRecords,
                        authorization,
                        CLOCK);
    }

    @Test
    void beginOrderCreateUsesOrderCreateDocumentType() {
        UUID documentId = UUID.randomUUID();
        when(documentEngine.createDocument(any(CreateDocumentCommand.class)))
                .thenReturn(metadata(documentId, DocumentTypeCode.ORDER_CREATE.name()));

        UUID result = service.beginOrderCreate("New order");

        assertEquals(documentId, result);
        verify(authorization).requirePermission(OrderManagementPermissions.ORDER_CREATE);
        ArgumentCaptor<CreateDocumentCommand> captor =
                ArgumentCaptor.forClass(CreateDocumentCommand.class);
        verify(documentEngine).createDocument(captor.capture());
        assertEquals("ORDER_CREATE", captor.getValue().documentTypeId());
        assertEquals("New order", captor.getValue().title());
    }

    @Test
    void saveCreateDraftCreatesOrderCreatePayloadViaDraftService() {
        UUID documentId = UUID.randomUUID();
        DocumentId id = DocumentId.of(documentId);
        when(draftPayloads.load(id)).thenReturn(Optional.empty());
        AtomicReference<OrderDocumentPayload> created = new AtomicReference<>();
        when(draftPayloads.createDraft(any(OrderDocumentPayload.class)))
                .thenAnswer(
                        invocation -> {
                            OrderDocumentPayload payload = invocation.getArgument(0);
                            created.set(payload);
                            return payload;
                        });

        OrderHeaderDraft draft =
                OrderHeaderDraft.of(
                        "ORD-1",
                        "C-1",
                        "Acme",
                        null,
                        null,
                        null,
                        "PRIVATE",
                        "RUB");
        long revision = service.saveCreateDraft(documentId, draft, 0L);

        assertEquals(0L, revision);
        verify(authorization).requirePermission(OrderManagementPermissions.ORDER_CREATE);
        OrderCreatePayload payload = assertInstanceOf(OrderCreatePayload.class, created.get());
        assertEquals("ORD-1", payload.orderNumber().value());
        assertEquals("Acme", payload.commercialData().customerName());
        assertEquals(DocumentTypeCode.ORDER_CREATE, payload.documentTypeCode());
    }

    @Test
    void postDocumentCallsDocumentEnginePostOnlyAndResolvesCreatedOrderId() {
        UUID documentId = UUID.randomUUID();
        OrderId orderId = OrderId.generate();
        OrderCreatePayload payload =
                OrderCreatePayload.create(
                        DocumentId.of(documentId),
                        OrderNumber.of("ORD-POST-1"),
                        commercial(),
                        NOW);
        when(documentEngine.findById(documentId))
                .thenReturn(Optional.of(metadata(documentId, DocumentTypeCode.ORDER_CREATE.name())));
        when(draftPayloads.load(DocumentId.of(documentId))).thenReturn(Optional.of(payload));
        when(documentEngine.postDocument(documentId))
                .thenReturn(metadata(documentId, DocumentTypeCode.ORDER_CREATE.name()));
        when(processingRecords.findByDocumentIdAndOperation(
                        DocumentId.of(documentId), ProcessingOperation.POST))
                .thenReturn(
                        Optional.of(
                                ProcessingRecord.completedPost(
                                        DocumentId.of(documentId),
                                        DocumentTypeCode.ORDER_CREATE,
                                        PayloadRevision.initial(),
                                        NOW,
                                        ResultReference.of("order:" + orderId.value()))));

        OrderId result = service.postDocument(documentId);

        assertEquals(orderId, result);
        verify(authorization).requirePermission(OrderManagementPermissions.ORDER_CREATE);
        verify(documentEngine).postDocument(documentId);
    }

    @Test
    void transferToWorkApprovesThenActivatesInOneOperation() {
        OrderId orderId = OrderId.generate();
        UUID approveDoc = UUID.randomUUID();
        UUID activateDoc = UUID.randomUUID();
        when(orderQueryService.getOrder(orderId))
                .thenReturn(Optional.of(orderDto(orderId, OrderStatus.DRAFT)))
                .thenReturn(Optional.of(orderDto(orderId, OrderStatus.APPROVED)));
        when(documentEngine.createDocument(any(CreateDocumentCommand.class)))
                .thenReturn(metadata(approveDoc, DocumentTypeCode.ORDER_APPROVE.name()))
                .thenReturn(metadata(activateDoc, DocumentTypeCode.ORDER_ACTIVATE.name()));
        when(draftPayloads.createDraft(any(OrderDocumentPayload.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(documentEngine.findById(approveDoc))
                .thenReturn(Optional.of(metadata(approveDoc, DocumentTypeCode.ORDER_APPROVE.name())));
        when(documentEngine.findById(activateDoc))
                .thenReturn(Optional.of(metadata(activateDoc, DocumentTypeCode.ORDER_ACTIVATE.name())));
        when(draftPayloads.load(DocumentId.of(approveDoc)))
                .thenReturn(Optional.of(OrderApprovePayload.create(DocumentId.of(approveDoc), orderId, NOW)));
        when(draftPayloads.load(DocumentId.of(activateDoc)))
                .thenReturn(Optional.of(OrderActivatePayload.create(DocumentId.of(activateDoc), orderId, NOW)));
        when(documentEngine.postDocument(any(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID id = invocation.getArgument(0);
                    String type =
                            id.equals(approveDoc)
                                    ? DocumentTypeCode.ORDER_APPROVE.name()
                                    : DocumentTypeCode.ORDER_ACTIVATE.name();
                    return metadata(id, type);
                });

        OrderId result = service.transferToWork(orderId);

        assertEquals(orderId, result);
        verify(documentEngine, times(2)).postDocument(any(UUID.class));
        verify(documentEngine).postDocument(approveDoc);
        verify(documentEngine).postDocument(activateDoc);
    }

    @Test
    void transferToWorkIsNoOpWhenAlreadyActive() {
        OrderId orderId = OrderId.generate();
        when(orderQueryService.getOrder(orderId))
                .thenReturn(Optional.of(orderDto(orderId, OrderStatus.ACTIVE)));

        assertEquals(orderId, service.transferToWork(orderId));
        verify(documentEngine, never()).createDocument(any());
        verify(documentEngine, never()).postDocument(any());
    }

    @Test
    void saveNewOrderBeginsSavesAndPostsAsOneOperation() {
        UUID documentId = UUID.randomUUID();
        OrderId created = OrderId.generate();
        when(documentEngine.createDocument(any(CreateDocumentCommand.class)))
                .thenReturn(metadata(documentId, DocumentTypeCode.ORDER_CREATE.name()));
        when(draftPayloads.load(DocumentId.of(documentId))).thenReturn(Optional.empty());
        when(draftPayloads.createDraft(any(OrderDocumentPayload.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(documentEngine.findById(documentId))
                .thenReturn(Optional.of(metadata(documentId, DocumentTypeCode.ORDER_CREATE.name())));
        when(draftPayloads.load(DocumentId.of(documentId)))
                .thenReturn(Optional.empty())
                .thenReturn(
                        Optional.of(
                                OrderCreatePayload.create(
                                        DocumentId.of(documentId),
                                        OrderNumber.of("ORD-NEW"),
                                        commercial(),
                                        NOW)));
        when(documentEngine.postDocument(documentId))
                .thenReturn(metadata(documentId, DocumentTypeCode.ORDER_CREATE.name()));
        when(processingRecords.findByDocumentIdAndOperation(
                        DocumentId.of(documentId), ProcessingOperation.POST))
                .thenReturn(
                        Optional.of(
                                ProcessingRecord.completedPost(
                                        DocumentId.of(documentId),
                                        DocumentTypeCode.ORDER_CREATE,
                                        PayloadRevision.initial(),
                                        NOW,
                                        ResultReference.of("order:" + created.value()))));

        OrderId result =
                service.saveNewOrder(
                        OrderHeaderDraft.of("ORD-NEW", "C-1", "Acme", null, null, null, "PRIVATE", "RUB"));

        assertEquals(created, result);
        verify(documentEngine).createDocument(any(CreateDocumentCommand.class));
        verify(documentEngine).postDocument(documentId);
    }

    private static OrderDto orderDto(OrderId id, OrderStatus status) {
        return OrderDto.of(
                id,
                "N-1",
                status,
                "C-1",
                "Acme",
                null,
                null,
                null,
                "PRIVATE",
                "RUB",
                NOW,
                NOW);
    }

    @Test
    void beginOrderActivateUsesOrderActivateDocumentType() {
        UUID documentId = UUID.randomUUID();
        OrderId orderId = OrderId.generate();
        when(documentEngine.createDocument(any(CreateDocumentCommand.class)))
                .thenReturn(metadata(documentId, DocumentTypeCode.ORDER_ACTIVATE.name()));

        UUID result = service.beginOrderActivate("Activate order", orderId);

        assertEquals(documentId, result);
        verify(authorization).requirePermission(OrderManagementPermissions.ORDER_APPROVE);
        ArgumentCaptor<CreateDocumentCommand> captor =
                ArgumentCaptor.forClass(CreateDocumentCommand.class);
        verify(documentEngine).createDocument(captor.capture());
        assertEquals("ORDER_ACTIVATE", captor.getValue().documentTypeId());
        verify(draftPayloads).createDraft(any());
    }

    @Test
    void beginOrderCreateRequiresCreatePermission() {
        when(documentEngine.createDocument(any(CreateDocumentCommand.class)))
                .thenReturn(metadata(UUID.randomUUID(), DocumentTypeCode.ORDER_CREATE.name()));

        service.beginOrderCreate("Title");

        verify(authorization).requirePermission(eq(OrderManagementPermissions.ORDER_CREATE));
    }

    @Test
    void defaultOrderDocumentUiServiceHasNoJdbcTemplateField() {
        for (Field field : DefaultOrderDocumentUiService.class.getDeclaredFields()) {
            assertFalse(
                    field.getType().getName().contains("JdbcTemplate"),
                    "Unexpected JdbcTemplate field: " + field.getName());
        }
        assertTrue(DefaultOrderDocumentUiService.class.getDeclaredFields().length >= 6);
    }

    @Test
    void saveCreateDraftRejectsNonZeroExpectedRevisionOnFirstSave() {
        UUID documentId = UUID.randomUUID();
        when(draftPayloads.load(DocumentId.of(documentId))).thenReturn(Optional.empty());
        OrderHeaderDraft draft =
                OrderHeaderDraft.of("ORD-2", null, "Acme", null, null, null, "DEALER", "USD");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.saveCreateDraft(documentId, draft, 1L));
        verify(draftPayloads, never()).createDraft(any());
    }

    private static DocumentMetadata metadata(UUID id, String typeId) {
        return new DocumentMetadata(
                id,
                typeId,
                "DOC-1",
                "title",
                DocumentStatus.DRAFT,
                0L,
                NOW,
                NOW,
                null,
                null);
    }

    private static OrderCommercialData commercial() {
        return OrderCommercialData.of(
                "C-1", "Acme", null, null, null, OrderDirection.PRIVATE, CurrencyCode.of("RUB"));
    }
}
