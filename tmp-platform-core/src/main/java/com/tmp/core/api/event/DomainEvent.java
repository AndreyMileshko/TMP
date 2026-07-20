package com.tmp.core.api.event;

import java.time.Instant;

/**
 * Marker for domain-level business events published through Platform Core.
 * Business semantics are defined by publishing capabilities, not by Core.
 */
public interface DomainEvent {

    String eventId();

    Instant occurredAt();

    String eventType();

    /**
     * Identifier of the capability that owns this event.
     */
    String sourceCapabilityId();
}
