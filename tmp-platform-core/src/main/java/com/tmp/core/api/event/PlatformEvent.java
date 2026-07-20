package com.tmp.core.api.event;

import java.time.Instant;

/**
 * Marker for infrastructure-level platform events with stable metadata.
 */
public interface PlatformEvent {

    String eventId();

    Instant occurredAt();

    String eventType();
}
