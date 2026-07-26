package com.tmp.order.application.order;

import com.tmp.order.api.OrderId;
import com.tmp.order.domain.CustomerOrder;
import com.tmp.order.domain.OptimisticLockConflictException;
import com.tmp.order.domain.OrderNumber;
import com.tmp.order.domain.repository.CustomerOrderRepository;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link CustomerOrderRepository} for unit tests. Increments aggregate version on
 * successful update; inserts keep version {@code 0}.
 */
public final class InMemoryCustomerOrderRepository implements CustomerOrderRepository {

    private final Map<OrderId, CustomerOrder> byId = new ConcurrentHashMap<>();
    private final Map<String, OrderId> byOrderNumber = new ConcurrentHashMap<>();

    @Override
    public CustomerOrder save(CustomerOrder order) {
        Objects.requireNonNull(order, "order");
        CustomerOrder existing = byId.get(order.id());
        if (existing == null) {
            if (byOrderNumber.putIfAbsent(order.orderNumber().value(), order.id()) != null) {
                throw new DuplicateOrderNumberException(order.orderNumber());
            }
            byId.put(order.id(), order);
            return order;
        }
        if (existing.version() != order.version()) {
            throw new OptimisticLockConflictException(
                    "Optimistic lock conflict for order " + order.id());
        }
        if (!existing.orderNumber().equals(order.orderNumber())) {
            throw new IllegalStateException("Order number is immutable: " + order.id());
        }
        CustomerOrder persisted =
                CustomerOrder.rehydrate(
                        order.id(),
                        order.orderNumber(),
                        order.commercialData(),
                        order.status(),
                        order.version() + 1L,
                        order.createdAt(),
                        order.updatedAt());
        byId.put(order.id(), persisted);
        return persisted;
    }

    @Override
    public Optional<CustomerOrder> findById(OrderId id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public boolean existsByOrderNumber(OrderNumber orderNumber) {
        Objects.requireNonNull(orderNumber, "orderNumber");
        return byOrderNumber.containsKey(orderNumber.value());
    }

    public int size() {
        return byId.size();
    }
}
