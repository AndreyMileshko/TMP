package com.tmp.production.application.event;

import com.tmp.core.api.event.DomainEvent;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Published after a successful whole-order Production Launch commit.
 */
public record OrderAcceptedIntoProduction(
        String eventId,
        Instant acceptedAt,
        UUID sourceOrderId,
        int acceptedItemCount,
        List<UUID> sourceOrderItemIds) implements DomainEvent {

    public static final String EVENT_TYPE = "production.order.accepted";
    private static final String SOURCE_CAPABILITY = "production";

    public OrderAcceptedIntoProduction {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(acceptedAt, "acceptedAt");
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        Objects.requireNonNull(sourceOrderItemIds, "sourceOrderItemIds");
        if (acceptedItemCount < 1) {
            throw new IllegalArgumentException("acceptedItemCount must be >= 1: " + acceptedItemCount);
        }
        sourceOrderItemIds = List.copyOf(sourceOrderItemIds);
        if (acceptedItemCount != sourceOrderItemIds.size()) {
            throw new IllegalArgumentException(
                    "acceptedItemCount must equal sourceOrderItemIds size: "
                            + acceptedItemCount
                            + " != "
                            + sourceOrderItemIds.size());
        }
        Set<UUID> unique = new HashSet<>(sourceOrderItemIds);
        if (unique.size() != sourceOrderItemIds.size()) {
            throw new IllegalArgumentException("sourceOrderItemIds must not contain duplicates");
        }
    }

    @Override
    public Instant occurredAt() {
        return acceptedAt;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public String sourceCapabilityId() {
        return SOURCE_CAPABILITY;
    }
}
