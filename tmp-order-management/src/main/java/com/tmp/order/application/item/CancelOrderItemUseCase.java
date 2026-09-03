package com.tmp.order.application.item;

import com.tmp.order.domain.OrderItem;
import com.tmp.order.domain.repository.CustomerOrderRepository;
import com.tmp.order.domain.repository.OrderItemRepository;
import java.time.Clock;
import java.util.Objects;

/**
 * Cancels a Draft order item (Specification §9.2). Stage 5 forbids {@code ACTIVE → CANCELLED}.
 *
 * <p>Revisions and specifications are retained.
 */
public final class CancelOrderItemUseCase {

    private final CustomerOrderRepository customerOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final Clock clock;

    public CancelOrderItemUseCase(
            CustomerOrderRepository customerOrderRepository,
            OrderItemRepository orderItemRepository,
            Clock clock) {
        this.customerOrderRepository =
                Objects.requireNonNull(customerOrderRepository, "customerOrderRepository");
        this.orderItemRepository =
                Objects.requireNonNull(orderItemRepository, "orderItemRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public OrderItem execute(CancelOrderItemCommand command) {
        Objects.requireNonNull(command, "command");
        OrderItem existing =
                orderItemRepository
                        .findById(command.orderItemId())
                        .orElseThrow(() -> new OrderItemNotFoundException(command.orderItemId()));
        ParentOrderDraftGuard.requireDraft(customerOrderRepository, existing.orderId());
        OrderItem cancelled = existing.cancel(clock);
        return orderItemRepository.save(cancelled);
    }
}
