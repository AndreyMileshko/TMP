package com.tmp.order.application.ui;

import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.ui.OrderDocumentUiService;
import com.tmp.order.api.ui.OrderHeaderDraft;
import com.tmp.order.application.document.OrderCreateDocumentProcessor;
import com.tmp.order.application.payload.DocumentId;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.DraftPayloadApplicationService;
import com.tmp.order.application.payload.OrderActivatePayload;
import com.tmp.order.application.payload.OrderApprovePayload;
import com.tmp.order.application.payload.OrderCancelPayload;
import com.tmp.order.application.payload.OrderCreatePayload;
import com.tmp.order.application.payload.OrderDocumentPayload;
import com.tmp.order.application.payload.OrderUpdatePayload;
import com.tmp.order.application.payload.PayloadOptimisticLockException;
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
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Default UI orchestration for order-level document flows. Posts only through {@link
 * DocumentEngine#postDocument(UUID)}; never invokes processors or repositories directly.
 */
public final class DefaultOrderDocumentUiService implements OrderDocumentUiService {

    private final DocumentEngine documentEngine;
    private final DraftPayloadApplicationService draftPayloads;
    private final OrderQueryService orderQueryService;
    private final ProcessingRecordPort processingRecords;
    private final AuthorizationService authorization;
    private final Clock clock;

    public DefaultOrderDocumentUiService(
            DocumentEngine documentEngine,
            DraftPayloadApplicationService draftPayloads,
            OrderQueryService orderQueryService,
            ProcessingRecordPort processingRecords,
            AuthorizationService authorization,
            Clock clock) {
        this.documentEngine = Objects.requireNonNull(documentEngine, "documentEngine");
        this.draftPayloads = Objects.requireNonNull(draftPayloads, "draftPayloads");
        this.orderQueryService = Objects.requireNonNull(orderQueryService, "orderQueryService");
        this.processingRecords = Objects.requireNonNull(processingRecords, "processingRecords");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public UUID beginOrderCreate(String title) {
        authorization.requirePermission(OrderManagementPermissions.ORDER_CREATE);
        return createDocument(DocumentTypeCode.ORDER_CREATE, title);
    }

    @Override
    public UUID beginOrderUpdate(String title, OrderId orderId) {
        Objects.requireNonNull(orderId, "orderId");
        authorization.requirePermission(OrderManagementPermissions.ORDER_EDIT);
        return createDocument(DocumentTypeCode.ORDER_UPDATE, title);
    }

    @Override
    public UUID beginOrderApprove(String title, OrderId orderId) {
        Objects.requireNonNull(orderId, "orderId");
        authorization.requirePermission(OrderManagementPermissions.ORDER_APPROVE);
        UUID documentId = createDocument(DocumentTypeCode.ORDER_APPROVE, title);
        Instant now = clock.instant();
        draftPayloads.createDraft(
                OrderApprovePayload.create(DocumentId.of(documentId), orderId, now));
        return documentId;
    }

    @Override
    public UUID beginOrderActivate(String title, OrderId orderId) {
        Objects.requireNonNull(orderId, "orderId");
        authorization.requirePermission(OrderManagementPermissions.ORDER_APPROVE);
        UUID documentId = createDocument(DocumentTypeCode.ORDER_ACTIVATE, title);
        Instant now = clock.instant();
        draftPayloads.createDraft(
                OrderActivatePayload.create(DocumentId.of(documentId), orderId, now));
        return documentId;
    }

    @Override
    public UUID beginOrderCancel(String title, OrderId orderId) {
        Objects.requireNonNull(orderId, "orderId");
        authorization.requirePermission(OrderManagementPermissions.ORDER_CANCEL);
        UUID documentId = createDocument(DocumentTypeCode.ORDER_CANCEL, title);
        Instant now = clock.instant();
        draftPayloads.createDraft(
                OrderCancelPayload.create(DocumentId.of(documentId), orderId, now));
        return documentId;
    }

    @Override
    public long saveCreateDraft(
            UUID documentId, OrderHeaderDraft draft, long expectedPayloadRevision) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(draft, "draft");
        authorization.requirePermission(OrderManagementPermissions.ORDER_CREATE);
        DocumentId id = DocumentId.of(documentId);
        Instant now = clock.instant();
        OrderNumber orderNumber = OrderNumber.of(draft.orderNumber());
        OrderCommercialData commercial = toCommercial(draft);

        Optional<OrderDocumentPayload> existing = draftPayloads.load(id);
        try {
            if (existing.isEmpty()) {
                requireExpectedInitialRevision(expectedPayloadRevision);
                OrderCreatePayload created =
                        OrderCreatePayload.create(id, orderNumber, commercial, now);
                return draftPayloads.createDraft(created).identity().payloadRevision().value();
            }
            OrderCreatePayload current = requireCreatePayload(existing.get(), id);
            PayloadRevision expected = PayloadRevision.of(expectedPayloadRevision);
            OrderCreatePayload candidate =
                    OrderCreatePayload.rehydrate(
                            current.identity().withNextRevision(now), orderNumber, commercial);
            return draftPayloads
                    .updateDraft(candidate, expected)
                    .identity()
                    .payloadRevision()
                    .value();
        } catch (PayloadOptimisticLockException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw wrap("Failed to save ORDER_CREATE draft for document " + documentId, ex);
        }
    }

    @Override
    public long saveUpdateDraft(
            UUID documentId,
            OrderId orderId,
            OrderHeaderDraft draft,
            long expectedPayloadRevision) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(draft, "draft");
        authorization.requirePermission(OrderManagementPermissions.ORDER_EDIT);
        DocumentId id = DocumentId.of(documentId);
        Instant now = clock.instant();
        OrderCommercialData commercial = toCommercial(draft);

        Optional<OrderDocumentPayload> existing = draftPayloads.load(id);
        try {
            if (existing.isEmpty()) {
                requireExpectedInitialRevision(expectedPayloadRevision);
                OrderUpdatePayload created =
                        OrderUpdatePayload.create(id, orderId, commercial, now);
                return draftPayloads.createDraft(created).identity().payloadRevision().value();
            }
            OrderUpdatePayload current = requireUpdatePayload(existing.get(), id);
            if (!current.orderId().equals(orderId)) {
                throw new IllegalArgumentException(
                        "ORDER_UPDATE draft orderId mismatch: expected "
                                + current.orderId()
                                + ", got "
                                + orderId);
            }
            PayloadRevision expected = PayloadRevision.of(expectedPayloadRevision);
            OrderUpdatePayload candidate = current.withCommercialData(commercial, now);
            return draftPayloads
                    .updateDraft(candidate, expected)
                    .identity()
                    .payloadRevision()
                    .value();
        } catch (PayloadOptimisticLockException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw wrap("Failed to save ORDER_UPDATE draft for document " + documentId, ex);
        }
    }

    @Override
    public OrderId postDocument(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId");
        DocumentMetadata metadata =
                documentEngine
                        .findById(documentId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Document not found: " + documentId));
        DocumentTypeCode type = DocumentTypeCode.valueOf(metadata.documentTypeId());
        requirePostPermission(type);

        DocumentId id = DocumentId.of(documentId);
        OrderDocumentPayload payload =
                draftPayloads
                        .load(id)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Typed payload missing for document " + documentId));

        try {
            documentEngine.postDocument(documentId);
        } catch (RuntimeException ex) {
            throw wrap("Failed to post document " + documentId, ex);
        }

        return switch (type) {
            case ORDER_CREATE -> resolveCreatedOrderId(id);
            case ORDER_UPDATE -> ((OrderUpdatePayload) payload).orderId();
            case ORDER_APPROVE -> ((OrderApprovePayload) payload).orderId();
            case ORDER_ACTIVATE -> ((OrderActivatePayload) payload).orderId();
            case ORDER_CANCEL -> ((OrderCancelPayload) payload).orderId();
            default ->
                    throw new IllegalStateException(
                            "OrderDocumentUiService does not support document type " + type);
        };
    }

    @Override
    public Optional<OrderHeaderDraft> loadCreateDraft(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId");
        authorization.requirePermission(OrderManagementPermissions.ORDER_CREATE);
        return draftPayloads
                .load(DocumentId.of(documentId))
                .filter(OrderCreatePayload.class::isInstance)
                .map(OrderCreatePayload.class::cast)
                .map(this::toCreateDraft);
    }

    @Override
    public Optional<OrderHeaderDraft> loadUpdateDraft(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId");
        authorization.requirePermission(OrderManagementPermissions.ORDER_EDIT);
        return draftPayloads
                .load(DocumentId.of(documentId))
                .filter(OrderUpdatePayload.class::isInstance)
                .map(OrderUpdatePayload.class::cast)
                .map(this::toUpdateDraft);
    }

    private UUID createDocument(DocumentTypeCode type, String title) {
        Objects.requireNonNull(title, "title");
        try {
            DocumentMetadata created =
                    documentEngine.createDocument(
                            new CreateDocumentCommand(type.name(), title));
            return created.id();
        } catch (RuntimeException ex) {
            throw wrap("Failed to create " + type.name() + " document", ex);
        }
    }

    private OrderId resolveCreatedOrderId(DocumentId documentId) {
        ProcessingRecord record =
                processingRecords
                        .findByDocumentIdAndOperation(documentId, ProcessingOperation.POST)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "ORDER_CREATE posted but processing record missing for "
                                                        + documentId));
        ResultReference result =
                record.resultReference()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "ORDER_CREATE processing record has no result for "
                                                        + documentId));
        return OrderCreateDocumentProcessor.orderIdFrom(result);
    }

    private void requirePostPermission(DocumentTypeCode type) {
        switch (type) {
            case ORDER_CREATE ->
                    authorization.requirePermission(OrderManagementPermissions.ORDER_CREATE);
            case ORDER_UPDATE ->
                    authorization.requirePermission(OrderManagementPermissions.ORDER_EDIT);
            case ORDER_APPROVE, ORDER_ACTIVATE ->
                    authorization.requirePermission(OrderManagementPermissions.ORDER_APPROVE);
            case ORDER_CANCEL ->
                    authorization.requirePermission(OrderManagementPermissions.ORDER_CANCEL);
            default ->
                    throw new IllegalStateException(
                            "Unsupported order document type for UI post: " + type);
        }
    }

    private static void requireExpectedInitialRevision(long expectedPayloadRevision) {
        if (expectedPayloadRevision != PayloadRevision.initial().value()) {
            throw new IllegalArgumentException(
                    "New draft payload expects revision 0, got " + expectedPayloadRevision);
        }
    }

    private static OrderCreatePayload requireCreatePayload(
            OrderDocumentPayload payload, DocumentId documentId) {
        if (!(payload instanceof OrderCreatePayload create)) {
            throw new IllegalStateException(
                    "Document " + documentId + " is not an ORDER_CREATE payload");
        }
        return create;
    }

    private static OrderUpdatePayload requireUpdatePayload(
            OrderDocumentPayload payload, DocumentId documentId) {
        if (!(payload instanceof OrderUpdatePayload update)) {
            throw new IllegalStateException(
                    "Document " + documentId + " is not an ORDER_UPDATE payload");
        }
        return update;
    }

    private OrderHeaderDraft toCreateDraft(OrderCreatePayload payload) {
        OrderCommercialData commercial = payload.commercialData();
        return OrderHeaderDraft.of(
                payload.orderNumber().value(),
                commercial.customerRef(),
                commercial.customerName(),
                commercial.contractRef(),
                commercial.siteRef(),
                commercial.responsibleManager(),
                commercial.direction().name(),
                commercial.currency().value());
    }

    private OrderHeaderDraft toUpdateDraft(OrderUpdatePayload payload) {
        OrderCommercialData commercial = payload.commercialData();
        String orderNumber =
                orderQueryService
                        .getOrder(payload.orderId())
                        .map(order -> order.orderNumber())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Order not found for update draft: "
                                                        + payload.orderId()));
        return OrderHeaderDraft.of(
                orderNumber,
                commercial.customerRef(),
                commercial.customerName(),
                commercial.contractRef(),
                commercial.siteRef(),
                commercial.responsibleManager(),
                commercial.direction().name(),
                commercial.currency().value());
    }

    private static OrderCommercialData toCommercial(OrderHeaderDraft draft) {
        return OrderCommercialData.of(
                draft.customerRef(),
                draft.customerName(),
                draft.contractRef(),
                draft.siteRef(),
                draft.responsibleManager(),
                OrderDirection.valueOf(draft.direction().trim()),
                CurrencyCode.of(draft.currency()));
    }

    private static RuntimeException wrap(String message, RuntimeException cause) {
        if (cause instanceof IllegalArgumentException
                || cause instanceof IllegalStateException
                || cause instanceof PayloadOptimisticLockException) {
            return cause;
        }
        return new IllegalStateException(message + ": " + cause.getMessage(), cause);
    }
}
