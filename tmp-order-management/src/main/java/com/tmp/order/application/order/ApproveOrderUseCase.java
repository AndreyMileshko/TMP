package com.tmp.order.application.order;

import com.tmp.order.domain.CustomerOrder;
import com.tmp.order.domain.OrderItem;
import com.tmp.order.domain.repository.CustomerOrderRepository;
import com.tmp.order.domain.repository.OrderItemRepository;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Approves a Draft customer order when at least one ACTIVE item exists and commercial data is
 * complete (Specification §8.2 / ADR-030).
 *
 * <p>Does not read or write Production Status. Persistence uses optimistic locking via
 * {@link CustomerOrderRepository#save}.
 */
public final class ApproveOrderUseCase {

    private final CustomerOrderRepository customerOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final Clock clock;

    public ApproveOrderUseCase(
            CustomerOrderRepository customerOrderRepository,
            OrderItemRepository orderItemRepository,
            Clock clock) {
        this.customerOrderRepository =
                Objects.requireNonNull(customerOrderRepository, "customerOrderRepository");
        this.orderItemRepository =
                Objects.requireNonNull(orderItemRepository, "orderItemRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CustomerOrder execute(ApproveOrderCommand command) {
        Objects.requireNonNull(command, "command");
        CustomerOrder existing =
                customerOrderRepository
                        .findById(command.orderId())
                        .orElseThrow(() -> new OrderNotFoundException(command.orderId()));
        List<String> missingCommercial =
                existing.commercialData().missingMandatoryFieldsForApproval();
        if (!missingCommercial.isEmpty()) {
            throw new OrderApprovalRejectedException(
                    command.orderId(),
                    "Order cannot be approved: missing mandatory commercial fields: "
                            + String.join(", ", missingCommercial),
                    missingCommercial);
        }
        List<OrderItem> items = orderItemRepository.findByOrderId(command.orderId());
        boolean hasActiveItem = items.stream().anyMatch(OrderItem::isActive);
        if (!hasActiveItem) {
            throw new OrderApprovalRejectedException(
                    command.orderId(),
                    "Order cannot be approved without at least one ACTIVE item");
        }
        CustomerOrder approved = existing.approve(clock);
        return customerOrderRepository.save(approved);
    }
}
