package com.tmp.core.api.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Marker for domain-level business events published through Platform Core.
 * Business semantics are defined by publishing capabilities, not by Core.
 */
public interface DomainEvent {

    default String eventId() {
        return UUID.randomUUID().toString();
    }

    default Instant occurredAt() {
        return Instant.now();
    }

    default String eventType() {
        return getClass().getSimpleName();
    }

    /**
     * Identifier of the capability that owns this event.
     */
    String sourceCapabilityId();
}
