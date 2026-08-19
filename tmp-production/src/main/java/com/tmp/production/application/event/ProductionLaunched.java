package com.tmp.production.application.event;

import com.tmp.core.api.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Published after a successful Production Launch commit.
 */
public record ProductionLaunched(
        String eventId,
        Instant occurredAt,
        UUID sourceOrderId,
        UUID sourceOrderItemId,
        UUID specificationId,
        long orderedQuantity) implements DomainEvent {

    public static final String EVENT_TYPE = "production.launched";
    private static final String SOURCE_CAPABILITY = "production";

    public ProductionLaunched {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
        Objects.requireNonNull(specificationId, "specificationId");
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
