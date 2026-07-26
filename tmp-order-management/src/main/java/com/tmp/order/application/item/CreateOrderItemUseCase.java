package com.tmp.order.application.item;

import com.tmp.order.api.OrderItemId;
import com.tmp.order.application.order.OrderNotFoundException;
import com.tmp.order.domain.CustomerOrder;
import com.tmp.order.domain.InvalidOrderStateException;
import com.tmp.order.domain.OrderItem;
import com.tmp.order.domain.repository.CustomerOrderRepository;
import com.tmp.order.domain.repository.OrderItemRepository;
import java.time.Clock;
import java.util.Objects;

/**
 * Creates a Draft order item with Revision 1 Draft under a Draft parent order (Specification §9.2).
 */
public final class CreateOrderItemUseCase {

    private final CustomerOrderRepository customerOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final Clock clock;

    public CreateOrderItemUseCase(
            CustomerOrderRepository customerOrderRepository,
            OrderItemRepository orderItemRepository,
            Clock clock) {
        this.customerOrderRepository =
                Objects.requireNonNull(customerOrderRepository, "customerOrderRepository");
        this.orderItemRepository =
                Objects.requireNonNull(orderItemRepository, "orderItemRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public OrderItem execute(CreateOrderItemCommand command) {
        Objects.requireNonNull(command, "command");
        CustomerOrder parent =
                customerOrderRepository
                        .findById(command.orderId())
                        .orElseThrow(() -> new OrderNotFoundException(command.orderId()));
        if (!parent.isDraft()) {
            throw new InvalidOrderStateException(
                    "Cannot add order item unless parent order is DRAFT, current="
                            + parent.status()
                            + ", orderId="
                            + parent.id());
        }
        if (orderItemRepository.findById(command.orderItemId()).isPresent()) {
            throw new DuplicateOrderItemIdException(command.orderItemId());
        }
        OrderItem item =
                OrderItem.create(
                        command.orderItemId(),
                        command.orderId(),
                        command.commercialData(),
                        command.orderedQuantity(),
                        clock);
        return orderItemRepository.save(item);
    }

    /** Thrown when payload reuses an existing {@link OrderItemId}. */
    public static final class DuplicateOrderItemIdException extends RuntimeException {

        private final OrderItemId orderItemId;

        public DuplicateOrderItemIdException(OrderItemId orderItemId) {
            super("Order item already exists: " + Objects.requireNonNull(orderItemId, "orderItemId"));
            this.orderItemId = orderItemId;
        }

        public OrderItemId orderItemId() {
            return orderItemId;
        }
    }
}
