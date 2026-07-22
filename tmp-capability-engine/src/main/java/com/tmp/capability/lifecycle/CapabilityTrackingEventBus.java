package com.tmp.capability.lifecycle;

import com.tmp.core.api.EventBus;
import com.tmp.core.api.event.DomainEvent;
import com.tmp.core.api.event.EventHandler;
import com.tmp.core.api.event.EventSubscription;
import com.tmp.core.api.event.PlatformEvent;
import java.util.Objects;

/**
 * Delegating event bus that records subscriptions created during capability lifecycle
 * callbacks so they can be released on rollback, failure, stop, or deactivation.
 */
public final class CapabilityTrackingEventBus implements EventBus {

    private final EventBus delegate;
    private final CapabilityEventSubscriptionRegistry subscriptionRegistry;

    public CapabilityTrackingEventBus(EventBus delegate, CapabilityEventSubscriptionRegistry subscriptionRegistry) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.subscriptionRegistry = Objects.requireNonNull(subscriptionRegistry, "subscriptionRegistry");
    }

    @Override
    public void publish(PlatformEvent event) {
        delegate.publish(event);
    }

    @Override
    public void publish(DomainEvent event) {
        delegate.publish(event);
    }

    @Override
    public EventSubscription subscribePlatform(
            Class<? extends PlatformEvent> eventType, EventHandler<PlatformEvent> handler) {
        EventSubscription subscription = delegate.subscribePlatform(eventType, handler);
        subscriptionRegistry.recordSubscription(subscription);
        return subscription;
    }

    @Override
    public EventSubscription subscribeDomain(
            Class<? extends DomainEvent> eventType, EventHandler<DomainEvent> handler) {
        EventSubscription subscription = delegate.subscribeDomain(eventType, handler);
        subscriptionRegistry.recordSubscription(subscription);
        return subscription;
    }
}
