package com.tmp.order.api.ui;

import com.tmp.order.api.OrderId;
import java.util.Optional;
import java.util.UUID;

/**
 * UI-facing orchestration for order-level document flows ({@code ORDER_CREATE}, {@code
 * ORDER_UPDATE}, {@code ORDER_APPROVE}, {@code ORDER_CANCEL}).
 *
 * <p>Posts only through Document Engine; never mutates aggregates directly.
 */
public interface OrderDocumentUiService {

    UUID beginOrderCreate(String title);

    UUID beginOrderUpdate(String title, OrderId orderId);

    UUID beginOrderApprove(String title, OrderId orderId);

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
     * Posts via {@code DocumentEngine.postDocument} only. For CREATE, resolves {@link OrderId} via
     * {@link com.tmp.order.api.OrderQueryService#searchOrders} by order number. For others returns
     * the known order id from the typed payload.
     */
    OrderId postDocument(UUID documentId);

    Optional<OrderHeaderDraft> loadCreateDraft(UUID documentId);

    Optional<OrderHeaderDraft> loadUpdateDraft(UUID documentId);
}
