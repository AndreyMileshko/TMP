package com.tmp.order.api.ui;

import com.tmp.order.api.OrderId;
import java.util.Optional;
import java.util.UUID;

/**
 * UI-facing orchestration for order-level document flows ({@code ORDER_CREATE}, {@code
 * ORDER_UPDATE}, {@code ORDER_APPROVE}, {@code ORDER_ACTIVATE}, {@code ORDER_CANCEL}).
 *
 * <p>Posts only through Document Engine; never mutates aggregates directly.
 */
public interface OrderDocumentUiService {

    UUID beginOrderCreate(String title);

    UUID beginOrderUpdate(String title, OrderId orderId);

    UUID beginOrderApprove(String title, OrderId orderId);

    UUID beginOrderActivate(String title, OrderId orderId);

    UUID beginOrderCancel(String title, OrderId orderId);

    /** Creates or updates typed create draft. Returns new payload revision. */
    long saveCreateDraft(UUID documentId, OrderHeaderDraft draft, long expectedPayloadRevision);

    /**
     * Creates or updates typed update draft. On first save, {@code orderId} seeds the payload;
     * subsequent saves must address the same order.
     *
     * @return new payload revision
     */
    long saveUpdateDraft(
            UUID documentId, OrderId orderId, OrderHeaderDraft draft, long expectedPayloadRevision);

    /**
     * Posts via {@code DocumentEngine.postDocument} only. For {@code ORDER_CREATE}, resolves the
     * created {@link OrderId} from the processing record result reference. For other supported
     * types returns the known order id from the typed payload. Does not use
     * {@link com.tmp.order.api.OrderQueryService#searchOrders}.
     */
    OrderId postDocument(UUID documentId);

    /**
     * User-facing Save for a new order: begin + save draft + post as one application operation.
     * Result is an editable {@link com.tmp.order.api.OrderStatus#DRAFT} order.
     */
    OrderId saveNewOrder(OrderHeaderDraft draft);

    /**
     * User-facing Save for an existing DRAFT order: begin update + save draft + post as one
     * application operation. Result remains {@link com.tmp.order.api.OrderStatus#DRAFT}.
     */
    OrderId saveExistingDraft(OrderId orderId, OrderHeaderDraft draft);

    /**
     * User-facing «Передать в работу»: approve (when DRAFT) then activate in one application
     * operation so the user is not left in {@code APPROVED} without {@code ACTIVE}.
     */
    OrderId transferToWork(OrderId orderId);

    Optional<OrderHeaderDraft> loadCreateDraft(UUID documentId);

    Optional<OrderHeaderDraft> loadUpdateDraft(UUID documentId);
}
