package com.tmp.order.application.order;

import com.tmp.order.domain.CustomerOrder;
import com.tmp.order.domain.repository.CustomerOrderRepository;
import java.time.Clock;
import java.util.Objects;

/**
 * Activates an APPROVED customer order to ACTIVE (Specification §8.2 / ADR-031).
 */
public final class ActivateOrderUseCase {

    private final CustomerOrderRepository customerOrderRepository;
    private final Clock clock;

    public ActivateOrderUseCase(CustomerOrderRepository customerOrderRepository, Clock clock) {
        this.customerOrderRepository =
                Objects.requireNonNull(customerOrderRepository, "customerOrderRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CustomerOrder execute(ActivateOrderCommand command) {
        Objects.requireNonNull(command, "command");
        CustomerOrder existing =
                customerOrderRepository
                        .findById(command.orderId())
                        .orElseThrow(() -> new OrderNotFoundException(command.orderId()));
        CustomerOrder activated = existing.activate(clock);
        return customerOrderRepository.save(activated);
    }
}
