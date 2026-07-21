package com.tmp.core.api.event.platform;

import com.tmp.core.api.event.AbstractPlatformEvent;
import java.time.Instant;

/**
 * Emitted when Platform Core completes startup of all registered components.
 */
public final class PlatformStartedEvent extends AbstractPlatformEvent {

    public PlatformStartedEvent() {
        super();
    }

    public PlatformStartedEvent(String eventId, Instant occurredAt) {
        super(eventId, occurredAt);
    }
}
