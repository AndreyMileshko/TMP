package com.tmp.core.event;

import com.tmp.core.api.event.PlatformEvent;

/**
 * Emitted when Platform Core completes startup of all registered components.
 */
public record PlatformStartedEvent() implements PlatformEvent {
}
