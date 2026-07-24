package com.tmp.order.domain.repository;

import com.tmp.order.api.OrderId;
import com.tmp.order.domain.CustomerOrder;
import com.tmp.order.domain.OptimisticLockConflictException;
import com.tmp.order.domain.OrderNumber;
import java.util.Optional;

/**
 * Domain port for Customer Order persistence (Specification §5.1 / §19).
 *
 * <p>{@link #save(CustomerOrder)} inserts or updates using the aggregate {@code version} for
 * optimistic locking and throws {@link OptimisticLockConflictException} on conflict. Absence is
 * expressed as {@link Optional#empty()}.
 */
public interface CustomerOrderRepository {

    /**
     * Inserts or updates the order.
     *
     * @param order aggregate to persist
     * @return the persisted aggregate (with updated version after successful write, as defined by
     *     the adapter)
     * @throws OptimisticLockConflictException when the stored version does not match
     */
    CustomerOrder save(CustomerOrder order);

    Optional<CustomerOrder> findById(OrderId id);

    boolean existsByOrderNumber(OrderNumber orderNumber);
}
