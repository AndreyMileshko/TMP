package com.tmp.order.application.order;

import com.tmp.order.domain.CustomerOrder;
import com.tmp.order.domain.repository.CustomerOrderRepository;
import java.time.Clock;
import java.util.Objects;

/**
 * Updates commercial header fields of an existing Draft customer order (Specification §8.2).
 *
 * <p>Does not change {@code OrderId}, {@code OrderNumber}, or status. Persistence uses optimistic
 * locking via {@link CustomerOrderRepository#save}.
 */
public final class UpdateOrderUseCase {

    private final CustomerOrderRepository customerOrderRepository;
    private final Clock clock;

    public UpdateOrderUseCase(CustomerOrderRepository customerOrderRepository, Clock clock) {
        this.customerOrderRepository =
                Objects.requireNonNull(customerOrderRepository, "customerOrderRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CustomerOrder execute(UpdateOrderCommand command) {
        Objects.requireNonNull(command, "command");
        CustomerOrder existing =
                customerOrderRepository
                        .findById(command.orderId())
                        .orElseThrow(() -> new OrderNotFoundException(command.orderId()));
        CustomerOrder updated =
                existing.updateCommercialData(command.commercialData(), clock);
        return customerOrderRepository.save(updated);
    }
}
