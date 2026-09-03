package com.tmp.order.application.item;

import com.tmp.order.domain.OrderItem;
import com.tmp.order.domain.repository.CustomerOrderRepository;
import com.tmp.order.domain.repository.OrderItemRepository;
import java.time.Clock;
import java.util.Objects;

/**
 * Updates only {@link com.tmp.order.domain.ItemCommercialData} of a Draft order item.
 */
public final class UpdateOrderItemUseCase {

    private final CustomerOrderRepository customerOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final Clock clock;

    public UpdateOrderItemUseCase(
            CustomerOrderRepository customerOrderRepository,
            OrderItemRepository orderItemRepository,
            Clock clock) {
        this.customerOrderRepository =
                Objects.requireNonNull(customerOrderRepository, "customerOrderRepository");
        this.orderItemRepository =
                Objects.requireNonNull(orderItemRepository, "orderItemRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public OrderItem execute(UpdateOrderItemCommand command) {
        Objects.requireNonNull(command, "command");
        OrderItem existing =
                orderItemRepository
                        .findById(command.orderItemId())
                        .orElseThrow(() -> new OrderItemNotFoundException(command.orderItemId()));
        ParentOrderDraftGuard.requireDraft(customerOrderRepository, existing.orderId());
        OrderItem updated = existing.updateCommercialData(command.commercialData(), clock);
        return orderItemRepository.save(updated);
    }
}
