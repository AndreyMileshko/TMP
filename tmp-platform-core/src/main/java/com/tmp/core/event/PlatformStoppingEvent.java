package com.tmp.core.event;

import com.tmp.core.api.event.PlatformEvent;

/**
 * Emitted when Platform Core begins shutdown of registered components.
 */
public record PlatformStoppingEvent() implements PlatformEvent {
}
