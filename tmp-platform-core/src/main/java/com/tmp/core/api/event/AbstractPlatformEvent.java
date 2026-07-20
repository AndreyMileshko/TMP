package com.tmp.core.api.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Base class for platform events with immutable metadata captured at construction time.
 */
public abstract class AbstractPlatformEvent implements PlatformEvent {

    private final String eventId;
    private final Instant occurredAt;

    protected AbstractPlatformEvent() {
        this(UUID.randomUUID().toString(), Instant.now());
    }

    protected AbstractPlatformEvent(String eventId, Instant occurredAt) {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
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
}
