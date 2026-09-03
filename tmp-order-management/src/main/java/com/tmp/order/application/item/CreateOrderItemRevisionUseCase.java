package com.tmp.order.application.item;

import com.tmp.order.api.RevisionNumber;
import com.tmp.order.domain.InvalidOrderStateException;
import com.tmp.order.domain.ItemSpecification;
import com.tmp.order.domain.OrderItem;
import com.tmp.order.domain.OrderItemRevision;
import com.tmp.order.domain.OrderedQuantity;
import com.tmp.order.domain.SpecificationLine;
import com.tmp.order.domain.repository.CustomerOrderRepository;
import com.tmp.order.domain.repository.OrderItemRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Creates Draft Revision {@code active+1} for an ACTIVE order item while the parent order is still
 * {@code DRAFT}. After transfer to work the parent is immutable and N+1 is rejected.
 */
public final class CreateOrderItemRevisionUseCase {

    private final CustomerOrderRepository customerOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final Clock clock;

    public CreateOrderItemRevisionUseCase(
            CustomerOrderRepository customerOrderRepository,
            OrderItemRepository orderItemRepository,
            Clock clock) {
        this.customerOrderRepository =
                Objects.requireNonNull(customerOrderRepository, "customerOrderRepository");
        this.orderItemRepository =
                Objects.requireNonNull(orderItemRepository, "orderItemRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public OrderItem execute(CreateOrderItemRevisionCommand command) {
        Objects.requireNonNull(command, "command");
        OrderItem existing =
                orderItemRepository
                        .findById(command.orderItemId())
                        .orElseThrow(() -> new OrderItemNotFoundException(command.orderItemId()));
        ParentOrderDraftGuard.requireDraft(customerOrderRepository, existing.orderId());
        RevisionNumber active =
                existing
                        .activeRevisionNumber()
                        .orElseThrow(
                                () ->
                                        new InvalidOrderStateException(
                                                "Cannot create next revision without an active"
                                                        + " revision: "
                                                        + existing.id()));
        RevisionNumber expected = active.next();
        if (!command.revisionNumber().equals(expected)) {
            throw new InvalidOrderStateException(
                    "Expected revision number "
                            + expected
                            + " but payload has "
                            + command.revisionNumber()
                            + " for item "
                            + existing.id());
        }
        OrderedQuantity quantity =
                existing.activeRevision().orElseThrow().orderedQuantity();
        OrderItem withDraft = existing.createNextDraftRevision(quantity, clock);
        if (command.copyFromRevisionNumber().isPresent()) {
            RevisionNumber copyFrom = command.copyFromRevisionNumber().orElseThrow();
            OrderItemRevision source =
                    existing
                            .revision(copyFrom)
                            .orElseThrow(
                                    () ->
                                            new InvalidOrderStateException(
                                                    "Copy-from revision does not exist: "
                                                            + copyFrom
                                                            + " on item "
                                                            + existing.id()));
            if (!source.isActive()) {
                throw new InvalidOrderStateException(
                        "Copy-from revision must be ACTIVE: " + copyFrom);
            }
            ItemSpecification copied =
                    copySpecification(
                            source.specification().orElse(ItemSpecification.empty(existing.id(), copyFrom)),
                            existing.id(),
                            command.revisionNumber());
            withDraft = withDraft.updateDraftSpecification(copied, clock);
        }
        return orderItemRepository.save(withDraft);
    }

    private static ItemSpecification copySpecification(
            ItemSpecification source, com.tmp.order.api.OrderItemId orderItemId, RevisionNumber target) {
        List<SpecificationLine> lines = new ArrayList<>(source.lines());
        return ItemSpecification.of(orderItemId, target, lines);
    }
}
