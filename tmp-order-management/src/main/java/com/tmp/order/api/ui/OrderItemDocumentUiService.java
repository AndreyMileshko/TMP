package com.tmp.order.api.ui;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * UI-facing orchestration for order item and revision document flows ({@code ORDER_ITEM_*} /
 * {@code ORDER_ITEM_REVISION_*}).
 *
 * <p>Posts only through Document Engine; never mutates aggregates directly and never invokes
 * processors or repositories from callers.
 */
public interface OrderItemDocumentUiService {

    UUID beginItemCreate(String title, OrderId orderId);

    UUID beginItemUpdate(String title, OrderItemId orderItemId);

    UUID beginItemCancel(String title, OrderItemId orderItemId);

    UUID beginRevisionCreate(String title, OrderItemId orderItemId);

    UUID beginRevisionUpdate(String title, OrderItemId orderItemId);

    UUID beginRevisionApprove(String title, OrderItemId orderItemId);

    /**
     * Creates or updates typed item-create draft. On first save generates {@link OrderItemId} when
     * {@code orderItemId} is empty.
     *
     * @return new payload revision
     */
    long saveItemCreateDraft(
            UUID documentId,
            OrderId orderId,
            Optional<OrderItemId> orderItemId,
            OrderItemCommercialDraft draft,
            String orderedQuantity,
            long expectedPayloadRevision);

    /** Creates or updates typed commercial update draft. */
    long saveItemUpdateDraft(
            UUID documentId,
            OrderItemId orderItemId,
            OrderItemCommercialDraft draft,
            long expectedPayloadRevision);

    /**
     * Creates or updates revision-create draft for {@code N+1}.
     *
     * @param copyFromRevisionNumber optional approved source revision
     */
    long saveRevisionCreateDraft(
            UUID documentId,
            OrderItemId orderItemId,
            RevisionNumber revisionNumber,
            Optional<RevisionNumber> copyFromRevisionNumber,
            long expectedPayloadRevision);

    /**
     * Creates or updates revision-update draft for the current Draft Revision with full
     * specification content. Line numbers are assigned sequentially from {@code 1} in list order.
     * Approved revisions are rejected.
     *
     * @return new payload revision
     */
    long saveRevisionUpdateDraft(
            UUID documentId,
            OrderItemId orderItemId,
            RevisionNumber revisionNumber,
            String orderedQuantity,
            List<OrderItemSpecificationLineDraft> specificationLines,
            long expectedPayloadRevision);

    /**
     * Creates or updates revision-update draft for the current Draft Revision. Ordered quantity is
     * updated; existing specification lines are preserved unchanged (delegates to the full-content
     * overload).
     */
    long saveRevisionUpdateDraft(
            UUID documentId,
            OrderItemId orderItemId,
            RevisionNumber revisionNumber,
            String orderedQuantity,
            long expectedPayloadRevision);

    /**
     * Posts via {@code DocumentEngine.postDocument} only. Returns the affected {@link OrderItemId}
     * from the typed payload or processing record.
     */
    OrderItemId postDocument(UUID documentId);

    Optional<OrderItemCommercialDraft> loadItemCreateDraft(UUID documentId);

    Optional<OrderItemCommercialDraft> loadItemUpdateDraft(UUID documentId);
}
