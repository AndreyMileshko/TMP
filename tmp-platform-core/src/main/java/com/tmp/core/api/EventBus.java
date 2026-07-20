package com.tmp.core.api;

import com.tmp.core.api.event.DomainEvent;
import com.tmp.core.api.event.EventHandler;
import com.tmp.core.api.event.EventSubscription;
import com.tmp.core.api.event.PlatformEvent;

/**
 * Synchronous in-process event bus for platform and domain events.
 */
public interface EventBus {

    void publish(PlatformEvent event);

    void publish(DomainEvent event);

    EventSubscription subscribePlatform(
            Class<? extends PlatformEvent> eventType, EventHandler<PlatformEvent> handler);

    EventSubscription subscribeDomain(
            Class<? extends DomainEvent> eventType, EventHandler<DomainEvent> handler);
}
