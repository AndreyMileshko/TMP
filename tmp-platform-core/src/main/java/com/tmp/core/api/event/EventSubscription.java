package com.tmp.core.api.event;

/**
 * Handle returned when subscribing to events. Unsubscribe releases the handler.
 */
public interface EventSubscription {

    void unsubscribe();

    boolean isActive();
}
