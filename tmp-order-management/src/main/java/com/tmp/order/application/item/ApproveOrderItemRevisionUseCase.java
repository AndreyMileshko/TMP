package com.tmp.order.application.item;

import com.tmp.order.api.RevisionNumber;
import com.tmp.order.domain.InvalidOrderStateException;
import com.tmp.order.domain.OrderItem;
import com.tmp.order.domain.repository.OrderItemRepository;
import java.time.Clock;
import java.util.Objects;

/**
 * Approves the current Draft Revision atomically (Specification §6.4).
 */
public final class ApproveOrderItemRevisionUseCase {

    private final OrderItemRepository orderItemRepository;
    private final Clock clock;

    public ApproveOrderItemRevisionUseCase(OrderItemRepository orderItemRepository, Clock clock) {
        this.orderItemRepository =
                Objects.requireNonNull(orderItemRepository, "orderItemRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public OrderItem execute(ApproveOrderItemRevisionCommand command) {
        Objects.requireNonNull(command, "command");
        OrderItem existing =
                orderItemRepository
                        .findById(command.orderItemId())
                        .orElseThrow(() -> new OrderItemNotFoundException(command.orderItemId()));
        RevisionNumber draftNumber =
                existing
                        .draftRevisionNumber()
                        .orElseThrow(
                                () ->
                                        new InvalidOrderStateException(
                                                "Cannot approve: no draft revision on item "
                                                        + existing.id()));
        if (!command.revisionNumber().equals(draftNumber)) {
            throw new InvalidOrderStateException(
                    "Payload revision "
                            + command.revisionNumber()
                            + " does not match draft revision "
                            + draftNumber
                            + " on item "
                            + existing.id());
        }
        OrderItem approved = existing.approveDraftRevision(clock);
        return orderItemRepository.save(approved);
    }
}
