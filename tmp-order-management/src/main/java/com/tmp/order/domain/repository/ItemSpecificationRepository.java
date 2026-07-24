package com.tmp.order.domain.repository;

import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.domain.ItemSpecification;
import java.util.Optional;

/**
 * Domain port for loading Item Specifications by typed identity (Specification §5.4 / §19).
 *
 * <p>Write path for specifications is the owning
 * {@link OrderItemRepository#save(com.tmp.order.domain.OrderItem)} (single aggregate boundary). This
 * port supports explicit load-by-key without a separate mutating API.
 */
public interface ItemSpecificationRepository {

    Optional<ItemSpecification> findByOrderItemIdAndRevisionNumber(
            OrderItemId orderItemId, RevisionNumber revisionNumber);
}
