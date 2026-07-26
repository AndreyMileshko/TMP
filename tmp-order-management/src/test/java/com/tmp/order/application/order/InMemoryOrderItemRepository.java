package com.tmp.order.application.order;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.domain.OptimisticLockConflictException;
import com.tmp.order.domain.OrderItem;
import com.tmp.order.domain.repository.OrderItemRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link OrderItemRepository} for unit tests.
 */
public final class InMemoryOrderItemRepository implements OrderItemRepository {

    private final Map<OrderItemId, OrderItem> byId = new ConcurrentHashMap<>();

    @Override
    public OrderItem save(OrderItem item) {
        Objects.requireNonNull(item, "item");
        OrderItem existing = byId.get(item.id());
        if (existing != null && existing.version() != item.version()) {
            throw new OptimisticLockConflictException(
                    "Optimistic lock conflict for order item " + item.id());
        }
        OrderItem persisted =
                OrderItem.rehydrate(
                        item.id(),
                        item.orderId(),
                        item.commercialData(),
                        item.status(),
                        item.activeRevisionNumber().orElse(null),
                        item.draftRevisionNumber().orElse(null),
                        item.revisions(),
                        existing == null ? item.version() : item.version() + 1L,
                        item.createdAt(),
                        item.updatedAt());
        byId.put(item.id(), persisted);
        return persisted;
    }

    @Override
    public Optional<OrderItem> findById(OrderItemId id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public List<OrderItem> findByOrderId(OrderId orderId) {
        Objects.requireNonNull(orderId, "orderId");
        List<OrderItem> result = new ArrayList<>();
        for (OrderItem item : byId.values()) {
            if (item.orderId().equals(orderId)) {
                result.add(item);
            }
        }
        return List.copyOf(result);
    }
}
