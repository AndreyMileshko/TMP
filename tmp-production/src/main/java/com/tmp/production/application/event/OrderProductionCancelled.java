package com.tmp.production.application.event;

import com.tmp.core.api.event.DomainEvent;
import com.tmp.production.domain.CancellationItemAction;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Published after a successful whole-order Production Cancellation document POST and transaction
 * commit (Production Spec §19).
 */
public record OrderProductionCancelled(
        String eventId,
        Instant cancelledAt,
        UUID productionCancellationDocumentId,
        UUID sourceOrderId,
        Optional<String> reason,
        List<ItemOutcome> itemOutcomes) implements DomainEvent {

    public static final String EVENT_TYPE = "production.order.cancelled";
    private static final String SOURCE_CAPABILITY = "production";

    public OrderProductionCancelled {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(cancelledAt, "cancelledAt");
        Objects.requireNonNull(productionCancellationDocumentId, "productionCancellationDocumentId");
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(itemOutcomes, "itemOutcomes");
        if (itemOutcomes.isEmpty()) {
            throw new IllegalArgumentException("itemOutcomes must not be empty");
        }
        itemOutcomes = List.copyOf(itemOutcomes);
        Set<UUID> uniqueItems = new HashSet<>();
        for (ItemOutcome outcome : itemOutcomes) {
            if (!uniqueItems.add(outcome.sourceOrderItemId())) {
                throw new IllegalArgumentException(
                        "itemOutcomes must not contain duplicate sourceOrderItemId: "
                                + outcome.sourceOrderItemId());
            }
        }
    }

    @Override
    public Instant occurredAt() {
        return cancelledAt;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public String sourceCapabilityId() {
        return SOURCE_CAPABILITY;
    }

    /** Whole-order cancellation outcome for one Production item line. */
    public record ItemOutcome(
            UUID sourceOrderItemId, UUID specificationId, CancellationItemAction action) {

        public ItemOutcome {
            Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
            Objects.requireNonNull(specificationId, "specificationId");
            Objects.requireNonNull(action, "action");
        }
    }
}
