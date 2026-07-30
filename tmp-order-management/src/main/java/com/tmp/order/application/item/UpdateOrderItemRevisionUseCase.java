package com.tmp.order.application.item;

import com.tmp.order.api.RevisionNumber;
import com.tmp.order.application.payload.OrderItemRevisionPayloadLine;
import com.tmp.order.domain.InvalidOrderStateException;
import com.tmp.order.domain.ItemSpecification;
import com.tmp.order.domain.OrderItem;
import com.tmp.order.domain.SpecificationLine;
import com.tmp.order.domain.repository.OrderItemRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Updates ordered quantity and Item Specification of the current Draft Revision only.
 */
public final class UpdateOrderItemRevisionUseCase {

    private final OrderItemRepository orderItemRepository;
    private final Clock clock;

    public UpdateOrderItemRevisionUseCase(OrderItemRepository orderItemRepository, Clock clock) {
        this.orderItemRepository =
                Objects.requireNonNull(orderItemRepository, "orderItemRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public OrderItem execute(UpdateOrderItemRevisionCommand command) {
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
                                                "No draft revision on item " + existing.id()));
        if (!command.revisionNumber().equals(draftNumber)) {
            throw new InvalidOrderStateException(
                    "Payload revision "
                            + command.revisionNumber()
                            + " does not match draft revision "
                            + draftNumber
                            + " on item "
                            + existing.id());
        }
        ItemSpecification specification =
                ItemSpecification.of(
                        existing.id(), command.revisionNumber(), toDomainLines(command.lines()));
        OrderItem updated =
                existing
                        .updateDraftOrderedQuantity(command.orderedQuantity(), clock)
                        .updateDraftSpecification(specification, clock);
        return orderItemRepository.save(updated);
    }

    private static List<SpecificationLine> toDomainLines(List<OrderItemRevisionPayloadLine> lines) {
        List<SpecificationLine> result = new ArrayList<>(lines.size());
        for (OrderItemRevisionPayloadLine line : lines) {
            result.add(
                    SpecificationLine.of(
                            line.materialCode(),
                            line.materialName(),
                            line.color(),
                            line.lengthMm(),
                            line.lineQuantity(),
                            line.unitOfMeasure()));
        }
        return result;
    }
}
