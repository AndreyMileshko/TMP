package com.tmp.order.application.order;

import com.tmp.order.api.OrderId;
import com.tmp.order.domain.CustomerOrder;
import com.tmp.order.domain.repository.CustomerOrderRepository;
import java.time.Clock;
import java.util.Objects;

/**
 * Creates a new customer order in {@code DRAFT} (Specification §8.2 / §15.2).
 *
 * <p>Enforces unique {@link com.tmp.order.domain.OrderNumber} before insert. Persistence goes only
 * through {@link CustomerOrderRepository}.
 */
public final class CreateOrderUseCase {

    private final CustomerOrderRepository customerOrderRepository;
    private final Clock clock;

    public CreateOrderUseCase(CustomerOrderRepository customerOrderRepository, Clock clock) {
        this.customerOrderRepository =
                Objects.requireNonNull(customerOrderRepository, "customerOrderRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CustomerOrder execute(CreateOrderCommand command) {
        Objects.requireNonNull(command, "command");
        if (customerOrderRepository.existsByOrderNumber(command.orderNumber())) {
            throw new DuplicateOrderNumberException(command.orderNumber());
        }
        CustomerOrder order =
                CustomerOrder.create(
                        OrderId.generate(),
                        command.orderNumber(),
                        command.commercialData(),
                        clock);
        return customerOrderRepository.save(order);
    }
}
