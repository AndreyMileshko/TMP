package com.tmp.order.application.ui;

import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.order.api.OrderDto;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderItemStatus;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.RevisionStatus;
import com.tmp.order.api.ui.OrderItemCommercialDraft;
import com.tmp.order.api.ui.OrderItemDocumentUiService;
import com.tmp.order.api.ui.OrderItemSpecificationLineDraft;
import com.tmp.order.application.document.OrderItemCreateDocumentProcessor;
import com.tmp.order.application.payload.DocumentId;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.DraftPayloadApplicationService;
import com.tmp.order.application.payload.OrderDocumentPayload;
import com.tmp.order.application.payload.OrderItemCancelPayload;
import com.tmp.order.application.payload.OrderItemCreatePayload;
import com.tmp.order.application.payload.OrderItemRevisionApprovePayload;
import com.tmp.order.application.payload.OrderItemRevisionCreatePayload;
import com.tmp.order.application.payload.OrderItemRevisionPayloadLine;
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
import com.tmp.order.domain.SpecificationLine;
import com.tmp.order.domain.repository.OrderItemRepository;
import com.tmp.security.api.AuthorizationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Default UI orchestration for item / revision document flows. Posts only through {@link
 * DocumentEngine#postDocument(UUID)}.
 */
public final class DefaultOrderItemDocumentUiService implements OrderItemDocumentUiService {

    private final DocumentEngine documentEngine;
    private final DraftPayloadApplicationService draftPayloads;
    private final OrderItemRepository orderItemRepository;
    private final OrderQueryService orderQueryService;
    private final ProcessingRecordPort processingRecords;
    private final AuthorizationService authorization;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public DefaultOrderItemDocumentUiService(
            DocumentEngine documentEngine,
            DraftPayloadApplicationService draftPayloads,
            OrderItemRepository orderItemRepository,
            ProcessingRecordPort processingRecords,
            AuthorizationService authorization,
            Clock clock) {
        this(
                documentEngine,
                draftPayloads,
                orderItemRepository,
                null,
                processingRecords,
                authorization,
                clock,
                null);
    }

    public DefaultOrderItemDocumentUiService(
            DocumentEngine documentEngine,
            DraftPayloadApplicationService draftPayloads,
            OrderItemRepository orderItemRepository,
            OrderQueryService orderQueryService,
            ProcessingRecordPort processingRecords,
            AuthorizationService authorization,
            Clock clock) {
        this(
                documentEngine,
                draftPayloads,
                orderItemRepository,
                orderQueryService,
                processingRecords,
                authorization,
                clock,
                null);
    }

    public DefaultOrderItemDocumentUiService(
            DocumentEngine documentEngine,
            DraftPayloadApplicationService draftPayloads,
            OrderItemRepository orderItemRepository,
            OrderQueryService orderQueryService,
            ProcessingRecordPort processingRecords,
            AuthorizationService authorization,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.documentEngine = Objects.requireNonNull(documentEngine, "documentEngine");
        this.draftPayloads = Objects.requireNonNull(draftPayloads, "draftPayloads");
        this.orderItemRepository =
                Objects.requireNonNull(orderItemRepository, "orderItemRepository");
        this.orderQueryService = orderQueryService;
        this.processingRecords = Objects.requireNonNull(processingRecords, "processingRecords");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.transactionTemplate =
                transactionManager == null ? null : new TransactionTemplate(transactionManager);
    }

    @Override
    public UUID beginItemCreate(String title, OrderId orderId) {
        Objects.requireNonNull(orderId, "orderId");
        authorization.requirePermission(OrderManagementPermissions.ITEM_CREATE);
        return createDocument(DocumentTypeCode.ORDER_ITEM_CREATE, title);
    }

    @Override
    public UUID beginItemUpdate(String title, OrderItemId orderItemId) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        authorization.requirePermission(OrderManagementPermissions.ITEM_EDIT);
        return createDocument(DocumentTypeCode.ORDER_ITEM_UPDATE, title);
    }

    @Override
    public UUID beginItemCancel(String title, OrderItemId orderItemId) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        authorization.requirePermission(OrderManagementPermissions.ITEM_CANCEL);
        UUID documentId = createDocument(DocumentTypeCode.ORDER_ITEM_CANCEL, title);
        Instant now = clock.instant();
        draftPayloads.createDraft(
                OrderItemCancelPayload.create(DocumentId.of(documentId), orderItemId, now));
        return documentId;
    }

    @Override
    public UUID beginRevisionCreate(String title, OrderItemId orderItemId) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        authorization.requirePermission(OrderManagementPermissions.REVISION_CREATE);
        return createDocument(DocumentTypeCode.ORDER_ITEM_REVISION_CREATE, title);
    }

    @Override
    public UUID beginRevisionUpdate(String title, OrderItemId orderItemId) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        authorization.requirePermission(OrderManagementPermissions.REVISION_EDIT);
        return createDocument(DocumentTypeCode.ORDER_ITEM_REVISION_UPDATE, title);
    }

    @Override
    public UUID beginRevisionApprove(String title, OrderItemId orderItemId) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        authorization.requirePermission(OrderManagementPermissions.ITEM_APPROVE);
        OrderItem item = requireItem(orderItemId);
        RevisionNumber draftNumber =
                item.draftRevisionNumber()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "No draft revision to approve on item "
                                                        + orderItemId));
        UUID documentId = createDocument(DocumentTypeCode.ORDER_ITEM_REVISION_APPROVE, title);
        Instant now = clock.instant();
        draftPayloads.createDraft(
                OrderItemRevisionApprovePayload.create(
                        DocumentId.of(documentId), orderItemId, draftNumber, now));
        return documentId;
    }

    @Override
    public long saveItemCreateDraft(
            UUID documentId,
            OrderId orderId,
            Optional<OrderItemId> orderItemId,
            OrderItemCommercialDraft draft,
            String orderedQuantity,
            long expectedPayloadRevision) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(orderItemId, "orderItemId");
        Objects.requireNonNull(draft, "draft");
        authorization.requirePermission(OrderManagementPermissions.ITEM_CREATE);
        DocumentId id = DocumentId.of(documentId);
        Instant now = clock.instant();
        ItemCommercialData commercial = toCommercial(draft);
        OrderedQuantity quantity = OrderedQuantity.of(parseQuantity(orderedQuantity));

        Optional<OrderDocumentPayload> existing = draftPayloads.load(id);
        try {
            if (existing.isEmpty()) {
                requireExpectedInitialRevision(expectedPayloadRevision);
                OrderItemId newItemId = orderItemId.orElseGet(OrderItemId::generate);
                OrderItemCreatePayload created =
                        OrderItemCreatePayload.create(
                                id, orderId, newItemId, commercial, quantity, now);
                return draftPayloads.createDraft(created).identity().payloadRevision().value();
            }
            OrderItemCreatePayload current = requireCreatePayload(existing.get(), id);
            if (!current.orderId().equals(orderId)) {
                throw new IllegalArgumentException(
                        "ORDER_ITEM_CREATE draft orderId mismatch: expected "
                                + current.orderId()
                                + ", got "
                                + orderId);
            }
            PayloadRevision expected = PayloadRevision.of(expectedPayloadRevision);
            OrderItemCreatePayload candidate =
                    OrderItemCreatePayload.rehydrate(
                            current.identity().withNextRevision(now),
                            current.orderId(),
                            current.orderItemId(),
                            commercial,
                            quantity);
            return draftPayloads
                    .updateDraft(candidate, expected)
                    .identity()
                    .payloadRevision()
                    .value();
        } catch (PayloadOptimisticLockException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw wrap("Failed to save ORDER_ITEM_CREATE draft for document " + documentId, ex);
        }
    }

    @Override
    public long saveItemUpdateDraft(
            UUID documentId,
            OrderItemId orderItemId,
            OrderItemCommercialDraft draft,
            long expectedPayloadRevision) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(orderItemId, "orderItemId");
        Objects.requireNonNull(draft, "draft");
        authorization.requirePermission(OrderManagementPermissions.ITEM_EDIT);
        DocumentId id = DocumentId.of(documentId);
        Instant now = clock.instant();
        ItemCommercialData commercial = toCommercial(draft);

        Optional<OrderDocumentPayload> existing = draftPayloads.load(id);
        try {
            if (existing.isEmpty()) {
                requireExpectedInitialRevision(expectedPayloadRevision);
                OrderItemUpdatePayload created =
                        OrderItemUpdatePayload.create(id, orderItemId, commercial, now);
                return draftPayloads.createDraft(created).identity().payloadRevision().value();
            }
            OrderItemUpdatePayload current = requireUpdatePayload(existing.get(), id);
            if (!current.orderItemId().equals(orderItemId)) {
                throw new IllegalArgumentException(
                        "ORDER_ITEM_UPDATE draft orderItemId mismatch: expected "
                                + current.orderItemId()
                                + ", got "
                                + orderItemId);
            }
            PayloadRevision expected = PayloadRevision.of(expectedPayloadRevision);
            OrderItemUpdatePayload candidate = current.withCommercialData(commercial, now);
            return draftPayloads
                    .updateDraft(candidate, expected)
                    .identity()
                    .payloadRevision()
                    .value();
        } catch (PayloadOptimisticLockException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw wrap("Failed to save ORDER_ITEM_UPDATE draft for document " + documentId, ex);
        }
    }

    @Override
    public long saveRevisionCreateDraft(
            UUID documentId,
            OrderItemId orderItemId,
            RevisionNumber revisionNumber,
            Optional<RevisionNumber> copyFromRevisionNumber,
            long expectedPayloadRevision) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(orderItemId, "orderItemId");
        Objects.requireNonNull(revisionNumber, "revisionNumber");
        Objects.requireNonNull(copyFromRevisionNumber, "copyFromRevisionNumber");
        authorization.requirePermission(OrderManagementPermissions.REVISION_CREATE);
        DocumentId id = DocumentId.of(documentId);
        Instant now = clock.instant();
        RevisionNumber copyFrom = copyFromRevisionNumber.orElse(null);

        Optional<OrderDocumentPayload> existing = draftPayloads.load(id);
        try {
            if (existing.isEmpty()) {
                requireExpectedInitialRevision(expectedPayloadRevision);
                OrderItemRevisionCreatePayload created =
                        OrderItemRevisionCreatePayload.create(
                                id, orderItemId, revisionNumber, copyFrom, now);
                return draftPayloads.createDraft(created).identity().payloadRevision().value();
            }
            OrderItemRevisionCreatePayload current = requireRevisionCreatePayload(existing.get(), id);
            if (!current.orderItemId().equals(orderItemId)) {
                throw new IllegalArgumentException(
                        "ORDER_ITEM_REVISION_CREATE draft orderItemId mismatch");
            }
            PayloadRevision expected = PayloadRevision.of(expectedPayloadRevision);
            OrderItemRevisionCreatePayload candidate =
                    OrderItemRevisionCreatePayload.rehydrate(
                            current.identity().withNextRevision(now),
                            orderItemId,
                            revisionNumber,
                            copyFrom);
            return draftPayloads
                    .updateDraft(candidate, expected)
                    .identity()
                    .payloadRevision()
                    .value();
        } catch (PayloadOptimisticLockException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw wrap(
                    "Failed to save ORDER_ITEM_REVISION_CREATE draft for document " + documentId,
                    ex);
        }
    }

    @Override
    public long saveRevisionUpdateDraft(
            UUID documentId,
            OrderItemId orderItemId,
            RevisionNumber revisionNumber,
            String orderedQuantity,
            long expectedPayloadRevision) {
        Objects.requireNonNull(documentId, "documentId");
        DocumentId id = DocumentId.of(documentId);
        Optional<OrderDocumentPayload> existing = draftPayloads.load(id);
        List<OrderItemSpecificationLineDraft> preserved;
        if (existing.isPresent() && existing.get() instanceof OrderItemRevisionUpdatePayload update) {
            preserved = toLineDrafts(update.copyLines());
        } else {
            preserved = toLineDrafts(loadDraftSpecificationLines(orderItemId, revisionNumber));
        }
        return saveRevisionUpdateDraft(
                documentId,
                orderItemId,
                revisionNumber,
                orderedQuantity,
                preserved,
                expectedPayloadRevision);
    }

    @Override
    public long saveRevisionUpdateDraft(
            UUID documentId,
            OrderItemId orderItemId,
            RevisionNumber revisionNumber,
            String orderedQuantity,
            List<OrderItemSpecificationLineDraft> specificationLines,
            long expectedPayloadRevision) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(orderItemId, "orderItemId");
        Objects.requireNonNull(revisionNumber, "revisionNumber");
        Objects.requireNonNull(specificationLines, "specificationLines");
        authorization.requirePermission(OrderManagementPermissions.REVISION_EDIT);
        requireEditableDraftRevision(orderItemId, revisionNumber);
        DocumentId id = DocumentId.of(documentId);
        Instant now = clock.instant();
        OrderedQuantity quantity = OrderedQuantity.of(parseQuantity(orderedQuantity));
        List<OrderItemRevisionPayloadLine> lines = toPayloadLines(specificationLines);

        Optional<OrderDocumentPayload> existing = draftPayloads.load(id);
        try {
            if (existing.isEmpty()) {
                requireExpectedInitialRevision(expectedPayloadRevision);
                OrderItemRevisionUpdatePayload created =
                        OrderItemRevisionUpdatePayload.create(
                                id, orderItemId, revisionNumber, quantity, lines, now);
                return draftPayloads.createDraft(created).identity().payloadRevision().value();
            }
            OrderItemRevisionUpdatePayload current =
                    requireRevisionUpdatePayload(existing.get(), id);
            if (!current.orderItemId().equals(orderItemId)
                    || !current.revisionNumber().equals(revisionNumber)) {
                throw new IllegalArgumentException(
                        "ORDER_ITEM_REVISION_UPDATE draft target mismatch");
            }
            PayloadRevision expected = PayloadRevision.of(expectedPayloadRevision);
            OrderItemRevisionUpdatePayload candidate = current.withContent(quantity, lines, now);
            return draftPayloads
                    .updateDraft(candidate, expected)
                    .identity()
                    .payloadRevision()
                    .value();
        } catch (PayloadOptimisticLockException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw wrap(
                    "Failed to save ORDER_ITEM_REVISION_UPDATE draft for document " + documentId,
                    ex);
        }
    }

    @Override
    public OrderItemId postDocument(UUID documentId) {
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
            case ORDER_ITEM_CREATE -> resolveCreatedOrderItemId(id);
            case ORDER_ITEM_UPDATE -> ((OrderItemUpdatePayload) payload).orderItemId();
            case ORDER_ITEM_CANCEL -> ((OrderItemCancelPayload) payload).orderItemId();
            case ORDER_ITEM_REVISION_CREATE ->
                    ((OrderItemRevisionCreatePayload) payload).orderItemId();
            case ORDER_ITEM_REVISION_UPDATE ->
                    ((OrderItemRevisionUpdatePayload) payload).orderItemId();
            case ORDER_ITEM_REVISION_APPROVE ->
                    ((OrderItemRevisionApprovePayload) payload).orderItemId();
            default ->
                    throw new IllegalStateException(
                            "OrderItemDocumentUiService does not support document type " + type);
        };
    }

    @Override
    public OrderItemId saveNewItem(
            OrderId orderId, OrderItemCommercialDraft draft, String orderedQuantity) {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(orderedQuantity, "orderedQuantity");
        return inTransaction(
                () -> {
                    UUID documentId =
                            beginItemCreate("ORDER_ITEM_CREATE " + orderId.value(), orderId);
                    saveItemCreateDraft(
                            documentId, orderId, Optional.empty(), draft, orderedQuantity, 0L);
                    return postDocument(documentId);
                });
    }

    @Override
    public OrderItemId saveExistingItem(
            OrderItemId orderItemId, OrderItemCommercialDraft draft, String orderedQuantity) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(orderedQuantity, "orderedQuantity");
        return inTransaction(
                () -> saveExistingItemInTransaction(orderItemId, draft, orderedQuantity));
    }

    private OrderItemId saveExistingItemInTransaction(
            OrderItemId orderItemId, OrderItemCommercialDraft draft, String orderedQuantity) {
        OrderItem item = requireItem(orderItemId);
        requireParentOrderDraft(item.orderId());
        if (item.status() == OrderItemStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Cancelled order item cannot be saved: " + orderItemId);
        }

        boolean commercialUpdated = false;
        boolean quantityUpdated = false;

        if (item.status() == OrderItemStatus.DRAFT) {
            UUID updateDoc =
                    beginItemUpdate(
                            "ORDER_ITEM_UPDATE " + orderItemId.value(), orderItemId);
            saveItemUpdateDraft(updateDoc, orderItemId, draft, 0L);
            postDocument(updateDoc);
            commercialUpdated = true;
            item = requireItem(orderItemId);
        }

        Optional<RevisionNumber> draftRevision = item.draftRevisionNumber();
        if (draftRevision.isPresent()) {
            RevisionNumber draftNumber = draftRevision.get();
            UUID revisionDoc =
                    beginRevisionUpdate(
                            "ORDER_ITEM_REVISION_UPDATE " + orderItemId.value(), orderItemId);
            saveRevisionUpdateDraft(
                    revisionDoc, orderItemId, draftNumber, orderedQuantity, 0L);
            postDocument(revisionDoc);
            quantityUpdated = true;
            item = requireItem(orderItemId);
        }

        if (shouldApproveDraftRevision(item)) {
            UUID approveDoc =
                    beginRevisionApprove(
                            "ORDER_ITEM_REVISION_APPROVE " + orderItemId.value(), orderItemId);
            postDocument(approveDoc);
            return orderItemId;
        }

        if (!commercialUpdated && !quantityUpdated) {
            throw new IllegalStateException(
                    "Order item has no editable commercial draft or draft revision to save: "
                            + orderItemId
                            + ", status="
                            + item.status());
        }
        return orderItemId;
    }

    /**
     * Approve only for an already-{@code ACTIVE} item that still has an open draft revision with a
     * non-empty specification. DRAFT items remain DRAFT after Save (iterative edit while parent is
     * DRAFT); promotion to ACTIVE is not part of Save for first revision.
     */
    private static boolean shouldApproveDraftRevision(OrderItem item) {
        if (item.status() != OrderItemStatus.ACTIVE) {
            return false;
        }
        Optional<OrderItemRevision> draft = item.draftRevision();
        if (draft.isEmpty() || !draft.get().isDraft()) {
            return false;
        }
        return draft.get()
                .specification()
                .filter(spec -> !spec.isEmpty())
                .isPresent();
    }

    private void requireParentOrderDraft(OrderId orderId) {
        if (orderQueryService == null) {
            return;
        }
        OrderDto parent =
                orderQueryService
                        .getOrder(orderId)
                        .orElseThrow(
                                () -> new IllegalStateException("Order not found: " + orderId));
        if (parent.status() != OrderStatus.DRAFT) {
            throw new IllegalStateException(
                    "Order item can be saved only while parent order is DRAFT, current="
                            + parent.status()
                            + ", orderId="
                            + orderId);
        }
    }

    @Override
    public Optional<OrderItemCommercialDraft> loadItemCreateDraft(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId");
        authorization.requirePermission(OrderManagementPermissions.ITEM_CREATE);
        return draftPayloads
                .load(DocumentId.of(documentId))
                .filter(OrderItemCreatePayload.class::isInstance)
                .map(OrderItemCreatePayload.class::cast)
                .map(this::toCommercialDraft);
    }

    @Override
    public Optional<OrderItemCommercialDraft> loadItemUpdateDraft(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId");
        authorization.requirePermission(OrderManagementPermissions.ITEM_EDIT);
        return draftPayloads
                .load(DocumentId.of(documentId))
                .filter(OrderItemUpdatePayload.class::isInstance)
                .map(OrderItemUpdatePayload.class::cast)
                .map(this::toCommercialDraft);
    }

    private UUID createDocument(DocumentTypeCode type, String title) {
        Objects.requireNonNull(title, "title");
        try {
            DocumentMetadata created =
                    documentEngine.createDocument(new CreateDocumentCommand(type.name(), title));
            return created.id();
        } catch (RuntimeException ex) {
            throw wrap("Failed to create " + type.name() + " document", ex);
        }
    }

    private OrderItemId resolveCreatedOrderItemId(DocumentId documentId) {
        ProcessingRecord record =
                processingRecords
                        .findByDocumentIdAndOperation(documentId, ProcessingOperation.POST)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "ORDER_ITEM_CREATE posted but processing record"
                                                        + " missing for "
                                                        + documentId));
        ResultReference result =
                record.resultReference()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "ORDER_ITEM_CREATE processing record has no result"
                                                        + " for "
                                                        + documentId));
        return OrderItemCreateDocumentProcessor.orderItemIdFrom(result);
    }

    private List<OrderItemRevisionPayloadLine> loadDraftSpecificationLines(
            OrderItemId orderItemId, RevisionNumber revisionNumber) {
        OrderItemRevision draft = requireEditableDraftRevision(orderItemId, revisionNumber);
        ItemSpecification specification =
                draft.specification().orElse(ItemSpecification.empty(orderItemId, revisionNumber));
        List<OrderItemRevisionPayloadLine> lines = new ArrayList<>();
        int lineNumber = 1;
        for (SpecificationLine line : specification.lines()) {
            lines.add(
                    OrderItemRevisionPayloadLine.of(
                            lineNumber++,
                            line.materialCode(),
                            line.materialName(),
                            line.color(),
                            line.lengthMm(),
                            line.lineQuantity(),
                            line.unitOfMeasure()));
        }
        return lines;
    }

    private OrderItemRevision requireEditableDraftRevision(
            OrderItemId orderItemId, RevisionNumber revisionNumber) {
        OrderItem item = requireItem(orderItemId);
        OrderItemRevision draft =
                item.draftRevision()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "No draft revision on item " + orderItemId));
        if (!draft.revisionNumber().equals(revisionNumber)) {
            throw new IllegalStateException(
                    "Draft revision mismatch: expected "
                            + revisionNumber
                            + ", got "
                            + draft.revisionNumber());
        }
        if (draft.status() != RevisionStatus.DRAFT) {
            throw new IllegalStateException(
                    "ORDER_ITEM_REVISION_UPDATE may address only a Draft Revision; got "
                            + draft.status());
        }
        Optional<OrderItemRevision> requested = item.revision(revisionNumber);
        if (requested.isEmpty()) {
            throw new IllegalArgumentException(
                    "Revision not found: " + orderItemId + "/" + revisionNumber);
        }
        if (requested.get().status() != RevisionStatus.DRAFT) {
            throw new IllegalStateException(
                    "Cannot update approved revision " + revisionNumber + " on item " + orderItemId);
        }
        return draft;
    }

    private static List<OrderItemRevisionPayloadLine> toPayloadLines(
            List<OrderItemSpecificationLineDraft> specificationLines) {
        List<OrderItemRevisionPayloadLine> lines = new ArrayList<>(specificationLines.size());
        int lineNumber = 1;
        for (OrderItemSpecificationLineDraft draft : specificationLines) {
            Objects.requireNonNull(draft, "specificationLines element");
            lines.add(
                    OrderItemRevisionPayloadLine.of(
                            lineNumber++,
                            draft.materialCode(),
                            draft.materialName(),
                            draft.color(),
                            draft.lengthMm(),
                            draft.lineQuantity(),
                            draft.unitOfMeasure()));
        }
        return lines;
    }

    private static List<OrderItemSpecificationLineDraft> toLineDrafts(
            List<OrderItemRevisionPayloadLine> lines) {
        List<OrderItemSpecificationLineDraft> drafts = new ArrayList<>(lines.size());
        for (OrderItemRevisionPayloadLine line : lines) {
            drafts.add(
                    OrderItemSpecificationLineDraft.of(
                            line.materialCode(),
                            line.materialName(),
                            line.color(),
                            line.lengthMm(),
                            line.lineQuantity(),
                            line.unitOfMeasure()));
        }
        return drafts;
    }

    private OrderItem requireItem(OrderItemId orderItemId) {
        return orderItemRepository
                .findById(orderItemId)
                .orElseThrow(
                        () -> new IllegalArgumentException("Order item not found: " + orderItemId));
    }

    private void requirePostPermission(DocumentTypeCode type) {
        switch (type) {
            case ORDER_ITEM_CREATE ->
                    authorization.requirePermission(OrderManagementPermissions.ITEM_CREATE);
            case ORDER_ITEM_UPDATE ->
                    authorization.requirePermission(OrderManagementPermissions.ITEM_EDIT);
            case ORDER_ITEM_CANCEL ->
                    authorization.requirePermission(OrderManagementPermissions.ITEM_CANCEL);
            case ORDER_ITEM_REVISION_CREATE ->
                    authorization.requirePermission(OrderManagementPermissions.REVISION_CREATE);
            case ORDER_ITEM_REVISION_UPDATE ->
                    authorization.requirePermission(OrderManagementPermissions.REVISION_EDIT);
            case ORDER_ITEM_REVISION_APPROVE ->
                    authorization.requirePermission(OrderManagementPermissions.ITEM_APPROVE);
            default ->
                    throw new IllegalStateException(
                            "Unsupported item document type for UI post: " + type);
        }
    }

    private static void requireExpectedInitialRevision(long expectedPayloadRevision) {
        if (expectedPayloadRevision != PayloadRevision.initial().value()) {
            throw new IllegalArgumentException(
                    "New draft payload expects revision 0, got " + expectedPayloadRevision);
        }
    }

    private static OrderItemCreatePayload requireCreatePayload(
            OrderDocumentPayload payload, DocumentId documentId) {
        if (!(payload instanceof OrderItemCreatePayload create)) {
            throw new IllegalStateException(
                    "Document " + documentId + " is not an ORDER_ITEM_CREATE payload");
        }
        return create;
    }

    private static OrderItemUpdatePayload requireUpdatePayload(
            OrderDocumentPayload payload, DocumentId documentId) {
        if (!(payload instanceof OrderItemUpdatePayload update)) {
            throw new IllegalStateException(
                    "Document " + documentId + " is not an ORDER_ITEM_UPDATE payload");
        }
        return update;
    }

    private static OrderItemRevisionCreatePayload requireRevisionCreatePayload(
            OrderDocumentPayload payload, DocumentId documentId) {
        if (!(payload instanceof OrderItemRevisionCreatePayload create)) {
            throw new IllegalStateException(
                    "Document " + documentId + " is not an ORDER_ITEM_REVISION_CREATE payload");
        }
        return create;
    }

    private static OrderItemRevisionUpdatePayload requireRevisionUpdatePayload(
            OrderDocumentPayload payload, DocumentId documentId) {
        if (!(payload instanceof OrderItemRevisionUpdatePayload update)) {
            throw new IllegalStateException(
                    "Document " + documentId + " is not an ORDER_ITEM_REVISION_UPDATE payload");
        }
        return update;
    }

    private OrderItemCommercialDraft toCommercialDraft(OrderItemCreatePayload payload) {
        ItemCommercialData commercial = payload.commercialData();
        return OrderItemCommercialDraft.of(
                commercial.productCode() == null ? null : commercial.productCode().value(),
                commercial.name(),
                commercial.comments(),
                commercial.externalPositionNumber());
    }

    private OrderItemCommercialDraft toCommercialDraft(OrderItemUpdatePayload payload) {
        ItemCommercialData commercial = payload.commercialData();
        return OrderItemCommercialDraft.of(
                commercial.productCode() == null ? null : commercial.productCode().value(),
                commercial.name(),
                commercial.comments(),
                commercial.externalPositionNumber());
    }

    private static ItemCommercialData toCommercial(OrderItemCommercialDraft draft) {
        return ItemCommercialData.ofRaw(
                draft.productCode(),
                draft.name(),
                draft.comments(),
                draft.externalPositionNumber());
    }

    private static BigDecimal parseQuantity(String orderedQuantity) {
        return UiProductQuantityNormalization.parseForOrderedQuantity(orderedQuantity);
    }

    private static RuntimeException wrap(String message, RuntimeException cause) {
        if (cause instanceof IllegalArgumentException
                || cause instanceof IllegalStateException
                || cause instanceof PayloadOptimisticLockException) {
            return cause;
        }
        return new IllegalStateException(message + ": " + cause.getMessage(), cause);
    }

    private <T> T inTransaction(java.util.function.Supplier<T> action) {
        if (transactionTemplate == null) {
            return action.get();
        }
        T result = transactionTemplate.execute(status -> action.get());
        if (result == null) {
            throw new IllegalStateException("Order item document orchestration returned null");
        }
        return result;
    }
}
