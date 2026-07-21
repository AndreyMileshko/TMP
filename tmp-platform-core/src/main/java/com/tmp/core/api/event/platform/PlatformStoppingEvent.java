package com.tmp.core.api.event.platform;

import com.tmp.core.api.event.AbstractPlatformEvent;
import java.time.Instant;

/**
 * Emitted when Platform Core begins shutdown of registered components.
 */
public final class PlatformStoppingEvent extends AbstractPlatformEvent {

    public PlatformStoppingEvent() {
        super();
    }

    public PlatformStoppingEvent(String eventId, Instant occurredAt) {
        super(eventId, occurredAt);
    }
}
