package com.tmp.order.application.ui;

import com.tmp.order.testsupport.IntakeContractFixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemDto;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.OrderItemStatus;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.RevisionStatus;
import com.tmp.order.api.ui.OrderItemCommercialDraft;
import com.tmp.order.api.ui.OrderItemEditorSnapshot;
import com.tmp.order.api.ui.OrderItemSpecificationLineDraft;
import com.tmp.order.application.payload.DocumentId;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.DraftPayloadApplicationService;
import com.tmp.order.application.payload.OrderDocumentPayload;
import com.tmp.order.application.payload.OrderItemCreatePayload;
import com.tmp.order.application.payload.OrderItemRevisionApprovePayload;
import com.tmp.order.application.payload.OrderItemRevisionUpdatePayload;
import com.tmp.order.application.payload.OrderItemUpdatePayload;
import com.tmp.order.application.payload.PayloadOptimisticLockException;
import com.tmp.order.application.processing.ProcessingOperation;
import com.tmp.order.application.processing.ProcessingRecord;
import com.tmp.order.application.processing.ProcessingRecordPort;
import com.tmp.order.application.processing.ResultReference;
import com.tmp.order.capability.OrderManagementPermissions;
import com.tmp.order.domain.ItemCommercialData;
import com.tmp.order.domain.ItemSpecification;
import com.tmp.order.domain.OrderItem;
import com.tmp.order.domain.OrderItemRevision;
import com.tmp.order.domain.OrderedQuantity;
import com.tmp.order.domain.PayloadRevision;
import com.tmp.order.domain.ProductCode;
import com.tmp.order.domain.SpecificationLine;
import com.tmp.order.domain.repository.OrderItemRepository;
import com.tmp.security.api.AuthorizationService;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DefaultOrderItemDocumentUiServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-27T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private DocumentEngine documentEngine;
    private DraftPayloadApplicationService draftPayloads;
    private OrderItemRepository orderItemRepository;
    private OrderQueryService orderQueryService;
    private ProcessingRecordPort processingRecords;
    private AuthorizationService authorization;
    private DefaultOrderItemDocumentUiService service;

    @BeforeEach
    void setUp() {
        documentEngine = mock(DocumentEngine.class);
        draftPayloads = mock(DraftPayloadApplicationService.class);
        orderItemRepository = mock(OrderItemRepository.class);
        orderQueryService = mock(OrderQueryService.class);
        processingRecords = mock(ProcessingRecordPort.class);
        authorization = mock(AuthorizationService.class);
        service =
                new DefaultOrderItemDocumentUiService(
                        documentEngine,
                        draftPayloads,
                        orderItemRepository,
                        orderQueryService,
                        processingRecords,
                        authorization,
                        CLOCK);
    }

    @Test
    void beginItemCreateUsesOrderItemCreateDocumentType() {
        UUID documentId = UUID.randomUUID();
        when(documentEngine.createDocument(any(CreateDocumentCommand.class)))
                .thenReturn(metadata(documentId, DocumentTypeCode.ORDER_ITEM_CREATE.name()));

        UUID result = service.beginItemCreate("item", OrderId.generate());

        assertEquals(documentId, result);
        verify(authorization).requirePermission(OrderManagementPermissions.ITEM_CREATE);
        ArgumentCaptor<CreateDocumentCommand> captor =
                ArgumentCaptor.forClass(CreateDocumentCommand.class);
        verify(documentEngine).createDocument(captor.capture());
        assertEquals("ORDER_ITEM_CREATE", captor.getValue().documentTypeId());
    }

    @Test
    void saveItemCreateDraftCreatesTypedPayload() {
        UUID documentId = UUID.randomUUID();
        DocumentId id = DocumentId.of(documentId);
        OrderId orderId = OrderId.generate();
        when(draftPayloads.load(id)).thenReturn(Optional.empty());
        AtomicReference<OrderDocumentPayload> created = new AtomicReference<>();
        when(draftPayloads.createDraft(any()))
                .thenAnswer(
                        invocation -> {
                            OrderDocumentPayload payload = invocation.getArgument(0);
                            created.set(payload);
                            return payload;
                        });

        long revision =
                service.saveItemCreateDraft(
                        documentId,
                        orderId,
                        Optional.empty(),
                        OrderItemCommercialDraft.of("P-1", "Panel", null),
                        "3",
                        0L);

        assertEquals(0L, revision);
        assertTrue(created.get() instanceof OrderItemCreatePayload);
        OrderItemCreatePayload payload = (OrderItemCreatePayload) created.get();
        assertEquals(orderId, payload.orderId());
        assertEquals("P-1", payload.commercialData().productCode().value());
        assertEquals(0, new BigDecimal("3").compareTo(payload.orderedQuantity().value()));
        verify(documentEngine, never()).registerProcessor(any());
    }

    @Test
    void saveItemCreateDraftAcceptsScaledWholeProductQuantity() {
        UUID documentId = UUID.randomUUID();
        DocumentId id = DocumentId.of(documentId);
        OrderId orderId = OrderId.generate();
        when(draftPayloads.load(id)).thenReturn(Optional.empty());
        AtomicReference<OrderDocumentPayload> created = new AtomicReference<>();
        when(draftPayloads.createDraft(any()))
                .thenAnswer(
                        invocation -> {
                            OrderDocumentPayload payload = invocation.getArgument(0);
                            created.set(payload);
                            return payload;
                        });

        service.saveItemCreateDraft(
                documentId,
                orderId,
                Optional.empty(),
                OrderItemCommercialDraft.of("P-1", "Panel", null),
                "8.000000",
                0L);

        OrderItemCreatePayload payload = (OrderItemCreatePayload) created.get();
        assertEquals(0, new BigDecimal("8").compareTo(payload.orderedQuantity().value()));
        assertEquals(0, payload.orderedQuantity().value().scale());
    }

    @Test
    void saveRevisionUpdateDraftPreservesExistingSpecificationLines() {
        UUID documentId = UUID.randomUUID();
        DocumentId id = DocumentId.of(documentId);
        OrderItemId itemId = OrderItemId.generate();
        OrderId orderId = OrderId.generate();
        RevisionNumber draftNumber = RevisionNumber.first();
        OrderItem item = draftItemWithSpecLine(orderId, itemId, draftNumber);
        when(orderItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(draftPayloads.load(id)).thenReturn(Optional.empty());
        AtomicReference<OrderDocumentPayload> created = new AtomicReference<>();
        when(draftPayloads.createDraft(any()))
                .thenAnswer(
                        invocation -> {
                            OrderDocumentPayload payload = invocation.getArgument(0);
                            created.set(payload);
                            return payload;
                        });

        service.saveRevisionUpdateDraft(documentId, itemId, draftNumber, "7", 0L);

        OrderItemRevisionUpdatePayload payload = (OrderItemRevisionUpdatePayload) created.get();
        assertEquals(1, payload.lines().size());
        assertEquals("MAT-1", payload.lines().get(0).materialCode());
        assertEquals(0, new BigDecimal("7").compareTo(payload.orderedQuantity().value()));
    }

    @Test
    void saveRevisionUpdateDraftWithLinesCreatesTypedPayloadAndAssignsLineNumbers() {
        UUID documentId = UUID.randomUUID();
        DocumentId id = DocumentId.of(documentId);
        OrderItemId itemId = OrderItemId.generate();
        OrderId orderId = OrderId.generate();
        RevisionNumber draftNumber = RevisionNumber.first();
        when(orderItemRepository.findById(itemId))
                .thenReturn(Optional.of(draftItemWithSpecLine(orderId, itemId, draftNumber)));
        when(draftPayloads.load(id)).thenReturn(Optional.empty());
        AtomicReference<OrderDocumentPayload> created = new AtomicReference<>();
        when(draftPayloads.createDraft(any()))
                .thenAnswer(
                        invocation -> {
                            OrderDocumentPayload payload = invocation.getArgument(0);
                            created.set(payload);
                            return payload;
                        });

        List<OrderItemSpecificationLineDraft> lines =
                List.of(
                        OrderItemSpecificationLineDraft.of(
                                "B", "Second", null, null, BigDecimal.TEN, "m"),
                        OrderItemSpecificationLineDraft.of(
                                "A", "First", "Red", BigDecimal.valueOf(1200), BigDecimal.ONE, "pcs"));
        service.saveRevisionUpdateDraft(documentId, itemId, draftNumber, "4", lines, 0L);

        OrderItemRevisionUpdatePayload payload = (OrderItemRevisionUpdatePayload) created.get();
        assertEquals(2, payload.lines().size());
        assertEquals(1, payload.lines().get(0).lineNumber());
        assertEquals("B", payload.lines().get(0).materialCode());
        assertEquals(2, payload.lines().get(1).lineNumber());
        assertEquals("A", payload.lines().get(1).materialCode());
        assertEquals(0, new BigDecimal("4").compareTo(payload.orderedQuantity().value()));
        verify(authorization).requirePermission(OrderManagementPermissions.REVISION_EDIT);
        verify(documentEngine, never()).registerProcessor(any());
    }

    @Test
    void saveRevisionUpdateDraftAllowsEmptyLines() {
        UUID documentId = UUID.randomUUID();
        DocumentId id = DocumentId.of(documentId);
        OrderItemId itemId = OrderItemId.generate();
        OrderId orderId = OrderId.generate();
        RevisionNumber draftNumber = RevisionNumber.first();
        when(orderItemRepository.findById(itemId))
                .thenReturn(Optional.of(draftItemWithSpecLine(orderId, itemId, draftNumber)));
        when(draftPayloads.load(id)).thenReturn(Optional.empty());
        AtomicReference<OrderDocumentPayload> created = new AtomicReference<>();
        when(draftPayloads.createDraft(any()))
                .thenAnswer(
                        invocation -> {
                            created.set(invocation.getArgument(0));
                            return invocation.getArgument(0);
                        });

        service.saveRevisionUpdateDraft(documentId, itemId, draftNumber, "1", List.of(), 0L);

        OrderItemRevisionUpdatePayload payload = (OrderItemRevisionUpdatePayload) created.get();
        assertTrue(payload.lines().isEmpty());
    }

    @Test
    void saveRevisionUpdateDraftRejectsApprovedRevision() {
        UUID documentId = UUID.randomUUID();
        OrderItemId itemId = OrderItemId.generate();
        OrderId orderId = OrderId.generate();
        OrderItem item = activeWithDraft(orderId, itemId);
        when(orderItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThrows(
                IllegalStateException.class,
                () ->
                        service.saveRevisionUpdateDraft(
                                documentId,
                                itemId,
                                RevisionNumber.first(),
                                "1",
                                List.of(),
                                0L));
    }

    @Test
    void saveRevisionUpdateDraftRejectsStalePayloadRevision() {
        UUID documentId = UUID.randomUUID();
        DocumentId id = DocumentId.of(documentId);
        OrderItemId itemId = OrderItemId.generate();
        OrderId orderId = OrderId.generate();
        RevisionNumber draftNumber = RevisionNumber.first();
        when(orderItemRepository.findById(itemId))
                .thenReturn(Optional.of(draftItemWithSpecLine(orderId, itemId, draftNumber)));
        OrderItemRevisionUpdatePayload existing =
                OrderItemRevisionUpdatePayload.create(
                        id,
                        itemId,
                        draftNumber,
                        OrderedQuantity.of(1),
                        List.of(),
                        NOW);
        when(draftPayloads.load(id)).thenReturn(Optional.of(existing));
        when(draftPayloads.updateDraft(any(), eq(PayloadRevision.of(0L))))
                .thenThrow(
                        new PayloadOptimisticLockException(
                                id, PayloadRevision.of(0L), PayloadRevision.of(1L)));

        assertThrows(
                PayloadOptimisticLockException.class,
                () ->
                        service.saveRevisionUpdateDraft(
                                documentId, itemId, draftNumber, "2", List.of(), 0L));
    }

    @Test
    void saveRevisionUpdateDraftIncrementsPayloadRevisionOnResave() {
        UUID documentId = UUID.randomUUID();
        DocumentId id = DocumentId.of(documentId);
        OrderItemId itemId = OrderItemId.generate();
        OrderId orderId = OrderId.generate();
        RevisionNumber draftNumber = RevisionNumber.first();
        when(orderItemRepository.findById(itemId))
                .thenReturn(Optional.of(draftItemWithSpecLine(orderId, itemId, draftNumber)));
        OrderItemRevisionUpdatePayload existing =
                OrderItemRevisionUpdatePayload.create(
                        id,
                        itemId,
                        draftNumber,
                        OrderedQuantity.of(1),
                        List.of(),
                        NOW);
        when(draftPayloads.load(id)).thenReturn(Optional.of(existing));
        when(draftPayloads.updateDraft(any(), eq(PayloadRevision.of(0L))))
                .thenAnswer(
                        invocation -> {
                            OrderItemRevisionUpdatePayload candidate = invocation.getArgument(0);
                            return candidate;
                        });

        long revision =
                service.saveRevisionUpdateDraft(
                        documentId, itemId, draftNumber, "3", List.of(), 0L);

        assertEquals(1L, revision);
    }

    @Test
    void beginRevisionUpdateUsesOrderItemRevisionUpdateDocumentType() {
        UUID documentId = UUID.randomUUID();
        when(documentEngine.createDocument(any(CreateDocumentCommand.class)))
                .thenReturn(metadata(documentId, DocumentTypeCode.ORDER_ITEM_REVISION_UPDATE.name()));

        UUID result = service.beginRevisionUpdate("rev-update", OrderItemId.generate());

        assertEquals(documentId, result);
        ArgumentCaptor<CreateDocumentCommand> captor =
                ArgumentCaptor.forClass(CreateDocumentCommand.class);
        verify(documentEngine).createDocument(captor.capture());
        assertEquals("ORDER_ITEM_REVISION_UPDATE", captor.getValue().documentTypeId());
        verify(authorization).requirePermission(OrderManagementPermissions.REVISION_EDIT);
    }

    @Test
    void editorQueryExposesDraftSeparatelyFromActive() {
        OrderItemId itemId = OrderItemId.generate();
        OrderId orderId = OrderId.generate();
        OrderItem item = activeWithDraft(orderId, itemId);
        when(orderItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        OrderQueryService orderQueryService = mock(OrderQueryService.class);
        when(orderQueryService.getOrderItem(itemId))
                .thenReturn(
                        Optional.of(
                                OrderItemDto.of(
                                        itemId,
                                        orderId,
                                        "P-2",
                                        "Door",
                                        null,
                                        "EXT-200",
                                        OrderItemStatus.ACTIVE,
                                        RevisionNumber.first(),
                                        NOW,
                                        NOW)));
        DefaultOrderItemEditorQueryService query =
                new DefaultOrderItemEditorQueryService(
                        orderItemRepository, orderQueryService, authorization);

        OrderItemEditorSnapshot snapshot = query.getEditorSnapshot(itemId).orElseThrow();

        assertTrue(snapshot.activeRevision().isPresent());
        assertTrue(snapshot.draftRevision().isPresent());
        assertEquals(1, snapshot.activeRevisionNumber().orElseThrow().value());
        assertEquals(2, snapshot.draftRevisionNumber().orElseThrow().value());
        assertEquals(1, snapshot.draftSpecificationLineCount());
        assertEquals("EXT-200", snapshot.externalPositionNumber());
        verify(authorization).requirePermission(OrderManagementPermissions.ITEM_VIEW);
    }

    @Test
    void editorQueryNormalizesScaledImportedProductQuantityForUi() {
        OrderItemId itemId = OrderItemId.generate();
        OrderId orderId = OrderId.generate();
        RevisionNumber draftNumber = RevisionNumber.first();
        OrderItemRevision draft =
                OrderItemRevision.rehydrate(
                        itemId,
                        draftNumber,
                        RevisionStatus.DRAFT,
                        OrderedQuantity.of(new BigDecimal("8.000000")),
                        null,
                        ItemSpecification.empty(itemId, draftNumber));
        OrderItem item =
                OrderItem.rehydrate(
                        itemId,
                        orderId,
                        ItemCommercialData.of(ProductCode.of("P-1"), "Panel", null),
                        OrderItemStatus.DRAFT,
                        null,
                        draftNumber,
                        Map.of(draftNumber, draft),
                        0L,
                        Instant.parse("2026-08-03T00:00:00Z"),
                        Instant.parse("2026-08-03T00:00:00Z"));
        when(orderItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        OrderQueryService orderQueryService = mock(OrderQueryService.class);
        when(orderQueryService.getOrderItem(itemId)).thenReturn(Optional.empty());
        DefaultOrderItemEditorQueryService query =
                new DefaultOrderItemEditorQueryService(
                        orderItemRepository, orderQueryService, authorization);

        OrderItemEditorSnapshot snapshot = query.getEditorSnapshot(itemId).orElseThrow();

        assertEquals(0, snapshot.orderedQuantity().scale());
        assertEquals("8", snapshot.orderedQuantity().toPlainString());
        assertEquals(
                "8",
                snapshot.draftRevision().orElseThrow().orderedQuantity().toPlainString());
    }

    @Test
    void editorQueryReturnsNullExternalPositionWhenAbsent() {
        OrderItemId itemId = OrderItemId.generate();
        OrderId orderId = OrderId.generate();
        OrderItem item = activeWithDraft(orderId, itemId, null);
        when(orderItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        OrderQueryService orderQueryService = mock(OrderQueryService.class);
        when(orderQueryService.getOrderItem(itemId)).thenReturn(Optional.empty());
        DefaultOrderItemEditorQueryService query =
                new DefaultOrderItemEditorQueryService(
                        orderItemRepository, orderQueryService, authorization);

        OrderItemEditorSnapshot snapshot = query.getEditorSnapshot(itemId).orElseThrow();

        assertEquals(null, snapshot.externalPositionNumber());
        verify(authorization).requirePermission(OrderManagementPermissions.ITEM_VIEW);
    }

    @Test
    void uiServiceHasNoJdbcTemplateField() {
        for (Field field : DefaultOrderItemDocumentUiService.class.getDeclaredFields()) {
            assertFalse(field.getType().getName().contains("JdbcTemplate"));
        }
    }

    @Test
    void saveNewItemBeginsSavesAndPostsAsOneOperation() {
        UUID documentId = UUID.randomUUID();
        OrderId orderId = OrderId.generate();
        OrderItemId created = OrderItemId.generate();
        when(documentEngine.createDocument(any(CreateDocumentCommand.class)))
                .thenReturn(metadata(documentId, DocumentTypeCode.ORDER_ITEM_CREATE.name()));
        when(draftPayloads.load(DocumentId.of(documentId))).thenReturn(Optional.empty());
        when(draftPayloads.createDraft(any(OrderDocumentPayload.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(documentEngine.findById(documentId))
                .thenReturn(
                        Optional.of(metadata(documentId, DocumentTypeCode.ORDER_ITEM_CREATE.name())));
        when(draftPayloads.load(DocumentId.of(documentId)))
                .thenReturn(Optional.empty())
                .thenReturn(
                        Optional.of(
                                OrderItemCreatePayload.create(
                                        DocumentId.of(documentId),
                                        orderId,
                                        created,
                                        ItemCommercialData.of(ProductCode.of("P-1"), "Panel", null),
                                        OrderedQuantity.of(3),
                                        NOW)));
        when(documentEngine.postDocument(documentId))
                .thenReturn(metadata(documentId, DocumentTypeCode.ORDER_ITEM_CREATE.name()));
        when(processingRecords.findByDocumentIdAndOperation(
                        DocumentId.of(documentId), ProcessingOperation.POST))
                .thenReturn(
                        Optional.of(
                                ProcessingRecord.completedPost(
                                        DocumentId.of(documentId),
                                        DocumentTypeCode.ORDER_ITEM_CREATE,
                                        PayloadRevision.initial(),
                                        NOW,
                                        ResultReference.of("orderItem:" + created.value()))));

        OrderItemId result =
                service.saveNewItem(
                        orderId, OrderItemCommercialDraft.of("P-1", "Panel", null), "3");

        assertEquals(created, result);
        verify(documentEngine).createDocument(any(CreateDocumentCommand.class));
        verify(documentEngine).postDocument(documentId);
    }

    @Test
    void saveExistingItemUpdatesCommercialAndDraftQuantityWithoutApprove() {
        OrderId orderId = OrderId.generate();
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber draftNumber = RevisionNumber.first();
        OrderItem draftItem = draftItemWithSpecLine(orderId, itemId, draftNumber);
        when(orderItemRepository.findById(itemId)).thenReturn(Optional.of(draftItem));
        when(orderQueryService.getOrder(orderId))
                .thenReturn(Optional.of(orderDto(orderId, OrderStatus.DRAFT)));

        UUID updateDoc = UUID.randomUUID();
        UUID revisionDoc = UUID.randomUUID();
        when(documentEngine.createDocument(any(CreateDocumentCommand.class)))
                .thenReturn(metadata(updateDoc, DocumentTypeCode.ORDER_ITEM_UPDATE.name()))
                .thenReturn(
                        metadata(revisionDoc, DocumentTypeCode.ORDER_ITEM_REVISION_UPDATE.name()));
        when(draftPayloads.load(any(DocumentId.class))).thenReturn(Optional.empty());
        when(draftPayloads.createDraft(any(OrderDocumentPayload.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(documentEngine.findById(updateDoc))
                .thenReturn(
                        Optional.of(metadata(updateDoc, DocumentTypeCode.ORDER_ITEM_UPDATE.name())));
        when(documentEngine.findById(revisionDoc))
                .thenReturn(
                        Optional.of(
                                metadata(
                                        revisionDoc,
                                        DocumentTypeCode.ORDER_ITEM_REVISION_UPDATE.name())));
        when(draftPayloads.load(DocumentId.of(updateDoc)))
                .thenReturn(Optional.empty())
                .thenReturn(
                        Optional.of(
                                OrderItemUpdatePayload.create(
                                        DocumentId.of(updateDoc),
                                        itemId,
                                        ItemCommercialData.of(ProductCode.of("P-9"), "Updated", null),
                                        NOW)));
        when(draftPayloads.load(DocumentId.of(revisionDoc)))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty())
                .thenReturn(
                        Optional.of(
                                OrderItemRevisionUpdatePayload.create(
                                        DocumentId.of(revisionDoc),
                                        itemId,
                                        draftNumber,
                                        OrderedQuantity.of(9),
                                        List.of(),
                                        NOW)));
        when(documentEngine.postDocument(updateDoc))
                .thenReturn(metadata(updateDoc, DocumentTypeCode.ORDER_ITEM_UPDATE.name()));
        when(documentEngine.postDocument(revisionDoc))
                .thenReturn(
                        metadata(revisionDoc, DocumentTypeCode.ORDER_ITEM_REVISION_UPDATE.name()));

        OrderItemId result =
                service.saveExistingItem(
                        itemId, OrderItemCommercialDraft.of("P-9", "Updated", null), "9");

        assertEquals(itemId, result);
        verify(documentEngine, times(2)).createDocument(any(CreateDocumentCommand.class));
        verify(documentEngine).postDocument(updateDoc);
        verify(documentEngine).postDocument(revisionDoc);
        verify(documentEngine, never())
                .createDocument(
                        org.mockito.ArgumentMatchers.argThat(
                                cmd ->
                                        cmd != null
                                                && "ORDER_ITEM_REVISION_APPROVE"
                                                        .equals(cmd.documentTypeId())));
    }

    @Test
    void saveExistingItemApprovesActiveItemDraftRevisionAfterQuantityUpdate() {
        OrderId orderId = OrderId.generate();
        OrderItemId itemId = OrderItemId.generate();
        OrderItem activeDraft = activeWithDraft(orderId, itemId);
        RevisionNumber draftNumber = activeDraft.draftRevisionNumber().orElseThrow();
        when(orderItemRepository.findById(itemId)).thenReturn(Optional.of(activeDraft));
        when(orderQueryService.getOrder(orderId))
                .thenReturn(Optional.of(orderDto(orderId, OrderStatus.DRAFT)));

        UUID revisionDoc = UUID.randomUUID();
        UUID approveDoc = UUID.randomUUID();
        when(documentEngine.createDocument(any(CreateDocumentCommand.class)))
                .thenReturn(
                        metadata(revisionDoc, DocumentTypeCode.ORDER_ITEM_REVISION_UPDATE.name()))
                .thenReturn(
                        metadata(approveDoc, DocumentTypeCode.ORDER_ITEM_REVISION_APPROVE.name()));
        when(draftPayloads.load(any(DocumentId.class))).thenReturn(Optional.empty());
        when(draftPayloads.createDraft(any(OrderDocumentPayload.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(documentEngine.findById(revisionDoc))
                .thenReturn(
                        Optional.of(
                                metadata(
                                        revisionDoc,
                                        DocumentTypeCode.ORDER_ITEM_REVISION_UPDATE.name())));
        when(documentEngine.findById(approveDoc))
                .thenReturn(
                        Optional.of(
                                metadata(
                                        approveDoc,
                                        DocumentTypeCode.ORDER_ITEM_REVISION_APPROVE.name())));
        when(draftPayloads.load(DocumentId.of(revisionDoc)))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty())
                .thenReturn(
                        Optional.of(
                                OrderItemRevisionUpdatePayload.create(
                                        DocumentId.of(revisionDoc),
                                        itemId,
                                        draftNumber,
                                        OrderedQuantity.of(8),
                                        List.of(),
                                        NOW)));
        when(draftPayloads.load(DocumentId.of(approveDoc)))
                .thenReturn(
                        Optional.of(
                                OrderItemRevisionApprovePayload.create(
                                        DocumentId.of(approveDoc), itemId, draftNumber, NOW)));
        when(documentEngine.postDocument(revisionDoc))
                .thenReturn(
                        metadata(revisionDoc, DocumentTypeCode.ORDER_ITEM_REVISION_UPDATE.name()));
        when(documentEngine.postDocument(approveDoc))
                .thenReturn(
                        metadata(approveDoc, DocumentTypeCode.ORDER_ITEM_REVISION_APPROVE.name()));

        OrderItemId result =
                service.saveExistingItem(
                        itemId, OrderItemCommercialDraft.of("P-2", "Door", null), "8");

        assertEquals(itemId, result);
        verify(documentEngine).postDocument(revisionDoc);
        verify(documentEngine).postDocument(approveDoc);
        ArgumentCaptor<CreateDocumentCommand> captor =
                ArgumentCaptor.forClass(CreateDocumentCommand.class);
        verify(documentEngine, times(2)).createDocument(captor.capture());
        assertEquals(
                "ORDER_ITEM_REVISION_UPDATE", captor.getAllValues().get(0).documentTypeId());
        assertEquals(
                "ORDER_ITEM_REVISION_APPROVE", captor.getAllValues().get(1).documentTypeId());
    }

    @Test
    void saveExistingItemRejectsWhenParentOrderNotDraft() {
        OrderId orderId = OrderId.generate();
        OrderItemId itemId = OrderItemId.generate();
        when(orderItemRepository.findById(itemId))
                .thenReturn(
                        Optional.of(
                                draftItemWithSpecLine(orderId, itemId, RevisionNumber.first())));
        when(orderQueryService.getOrder(orderId))
                .thenReturn(Optional.of(orderDto(orderId, OrderStatus.ACTIVE)));

        assertThrows(
                IllegalStateException.class,
                () ->
                        service.saveExistingItem(
                                itemId,
                                OrderItemCommercialDraft.of("P-1", "Panel", null),
                                "1"));
        verify(documentEngine, never()).createDocument(any());
    }

    private static com.tmp.order.api.OrderDto orderDto(OrderId id, OrderStatus status) {
        return com.tmp.order.api.OrderDto.of(
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

    private static OrderItem draftItemWithSpecLine(
            OrderId orderId, OrderItemId itemId, RevisionNumber draftNumber) {
        ItemSpecification specification =
                ItemSpecification.of(
                        itemId,
                        draftNumber,
                        List.of(
                                IntakeContractFixtures.specLine("MAT-1", "Material", BigDecimal.ONE, "pcs")));
        OrderItemRevision draft =
                OrderItemRevision.rehydrate(
                        itemId,
                        draftNumber,
                        RevisionStatus.DRAFT,
                        OrderedQuantity.of(1),
                        null,
                        specification);
        return OrderItem.rehydrate(
                itemId,
                orderId,
                ItemCommercialData.of(ProductCode.of("P-1"), "Panel", null),
                OrderItemStatus.DRAFT,
                null,
                draftNumber,
                Map.of(draftNumber, draft),
                0L,
                NOW,
                NOW);
    }

    private static OrderItem activeWithDraft(OrderId orderId, OrderItemId itemId) {
        return activeWithDraft(orderId, itemId, "EXT-200");
    }

    private static OrderItem activeWithDraft(
            OrderId orderId, OrderItemId itemId, String externalPositionNumber) {
        RevisionNumber activeNumber = RevisionNumber.first();
        RevisionNumber draftNumber = activeNumber.next();
        ItemSpecification activeSpec =
                ItemSpecification.rehydrate(
                        itemId,
                        activeNumber,
                        List.of(
                                IntakeContractFixtures.specLine("MAT-A", "A", BigDecimal.ONE, "pcs")),
                        true);
        ItemSpecification draftSpec =
                ItemSpecification.of(
                        itemId,
                        draftNumber,
                        List.of(
                                IntakeContractFixtures.specLine("MAT-D", "D", BigDecimal.TEN, "pcs")));
        OrderItemRevision active =
                OrderItemRevision.rehydrate(
                        itemId,
                        activeNumber,
                        RevisionStatus.ACTIVE,
                        OrderedQuantity.of(2),
                        null,
                        activeSpec);
        OrderItemRevision draft =
                OrderItemRevision.rehydrate(
                        itemId,
                        draftNumber,
                        RevisionStatus.DRAFT,
                        OrderedQuantity.of(5),
                        activeNumber,
                        draftSpec);
        return OrderItem.rehydrate(
                itemId,
                orderId,
                ItemCommercialData.of(ProductCode.of("P-2"), "Door", null, externalPositionNumber),
                OrderItemStatus.ACTIVE,
                activeNumber,
                draftNumber,
                Map.of(activeNumber, active, draftNumber, draft),
                1L,
                NOW,
                NOW);
    }

    private static DocumentMetadata metadata(UUID id, String type) {
        return new DocumentMetadata(
                id, type, "DOC-1", "title", DocumentStatus.DRAFT, 0L, NOW, NOW, null, null);
    }
}
