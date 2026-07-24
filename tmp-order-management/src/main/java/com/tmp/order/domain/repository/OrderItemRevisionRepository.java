package com.tmp.order.domain.repository;

import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.domain.OrderItemRevision;
import java.util.List;
import java.util.Optional;

/**
 * Domain port for loading Order Item Revisions by typed identity (Specification §5.3 / §19).
 *
 * <p>Write path for revisions is the owning {@link OrderItemRepository#save(com.tmp.order.domain.OrderItem)}
 * (single aggregate boundary). This port supports explicit load-by-key without requiring a separate
 * mutating API.
 */
public interface OrderItemRevisionRepository {

    Optional<OrderItemRevision> findByOrderItemIdAndRevisionNumber(
            OrderItemId orderItemId, RevisionNumber revisionNumber);

    List<OrderItemRevision> findByOrderItemId(OrderItemId orderItemId);
}
