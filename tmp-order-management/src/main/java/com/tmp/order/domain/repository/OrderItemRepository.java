package com.tmp.order.domain.repository;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.domain.OptimisticLockConflictException;
import com.tmp.order.domain.OrderItem;
import java.util.List;
import java.util.Optional;

/**
 * Domain port for Order Item aggregate persistence (Specification §5.2 / §19).
 *
 * <p>The Order Item aggregate includes its revisions and item specifications; adapters must load
 * and save them atomically with the item. {@link #save(OrderItem)} uses optimistic locking via
 * {@code version} and throws {@link OptimisticLockConflictException} on conflict.
 */
public interface OrderItemRepository {

    /**
     * Inserts or updates the order item aggregate (including revisions and specifications).
     *
     * @throws OptimisticLockConflictException when the stored version does not match
     */
    OrderItem save(OrderItem item);

    Optional<OrderItem> findById(OrderItemId id);

    /**
     * Loads all items belonging to the given order (required for aggregate workflows such as
     * approve-order preconditions). Not a Public Query API.
     */
    List<OrderItem> findByOrderId(OrderId orderId);
}
