package com.tmp.core.api.event;

import java.util.Objects;

/**
 * Typed handler for events delivered by the Event Bus.
 */
@FunctionalInterface
public interface EventHandler<T> {

    void handle(T event);
}
