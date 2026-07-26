package com.tmp.order.application.order;

import com.tmp.order.domain.CustomerOrder;
import com.tmp.order.domain.repository.CustomerOrderRepository;
import java.time.Clock;
import java.util.Objects;

/**
 * Cancels a Draft customer order (Specification §8.2). Stage 5 forbids {@code APPROVED → CANCELLED}.
 *
 * <p>Compensating documents for approved orders are out of scope.
 */
public final class CancelOrderUseCase {

    private final CustomerOrderRepository customerOrderRepository;
    private final Clock clock;

    public CancelOrderUseCase(CustomerOrderRepository customerOrderRepository, Clock clock) {
        this.customerOrderRepository =
                Objects.requireNonNull(customerOrderRepository, "customerOrderRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CustomerOrder execute(CancelOrderCommand command) {
        Objects.requireNonNull(command, "command");
        CustomerOrder existing =
                customerOrderRepository
                        .findById(command.orderId())
                        .orElseThrow(() -> new OrderNotFoundException(command.orderId()));
        CustomerOrder cancelled = existing.cancel(clock);
        return customerOrderRepository.save(cancelled);
    }
}
