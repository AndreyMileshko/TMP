package com.tmp.core.api.event;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Base class for domain events with immutable metadata captured at construction time.
 */
@SuppressFBWarnings(
        value = "CT_CONSTRUCTOR_THROW",
        justification = "Constructor validates required domain event metadata before use.")
public abstract class AbstractDomainEvent implements DomainEvent {

    private final String eventId;
    private final Instant occurredAt;
    private final String sourceCapabilityId;

    protected AbstractDomainEvent(String sourceCapabilityId) {
        this(UUID.randomUUID().toString(), Instant.now(), sourceCapabilityId);
    }

    protected AbstractDomainEvent(String eventId, Instant occurredAt, String sourceCapabilityId) {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.sourceCapabilityId = requireSourceCapabilityId(sourceCapabilityId);
    }

    private static String requireSourceCapabilityId(String sourceCapabilityId) {
        if (sourceCapabilityId == null || sourceCapabilityId.isBlank()) {
            throw new IllegalArgumentException("sourceCapabilityId must not be blank");
        }
        return sourceCapabilityId;
    }

    @Override
    public final String eventId() {
        return eventId;
    }

    @Override
    public final Instant occurredAt() {
        return occurredAt;
    }

    @Override
    public String eventType() {
        return getClass().getSimpleName();
    }

    @Override
    public final String sourceCapabilityId() {
        return sourceCapabilityId;
    }
}
