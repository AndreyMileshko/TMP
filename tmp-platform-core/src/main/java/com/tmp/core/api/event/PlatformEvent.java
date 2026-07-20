package com.tmp.core.api.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Marker for infrastructure-level platform events.
 */
public interface PlatformEvent {

    default String eventId() {
        return UUID.randomUUID().toString();
    }

    default Instant occurredAt() {
        return Instant.now();
    }

    default String eventType() {
        return getClass().getSimpleName();
    }
}
