package com.tmp.order.application.item;

import com.tmp.order.api.OrderId;
import com.tmp.order.application.order.OrderNotFoundException;
import com.tmp.order.domain.CustomerOrder;
import com.tmp.order.domain.InvalidOrderStateException;
import com.tmp.order.domain.repository.CustomerOrderRepository;

/**
 * After transfer to work the parent order is no longer DRAFT and item/revision/specification
 * mutations are forbidden.
 */
final class ParentOrderDraftGuard {

    private ParentOrderDraftGuard() {}

    static void requireDraft(CustomerOrderRepository orders, OrderId orderId) {
        CustomerOrder parent =
                orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!parent.isDraft()) {
            throw new InvalidOrderStateException(
                    "Order and its items are immutable after transfer to work, current="
                            + parent.status()
                            + ", orderId="
                            + parent.id());
        }
    }
}
