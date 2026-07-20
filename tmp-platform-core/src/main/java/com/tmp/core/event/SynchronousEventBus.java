package com.tmp.core.event;

import com.tmp.core.api.EventBus;
import com.tmp.core.api.event.DomainEvent;
import com.tmp.core.api.event.EventHandler;
import com.tmp.core.api.event.EventSubscription;
import com.tmp.core.api.event.PlatformEvent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Synchronous in-process Event Bus. No message broker dependencies.
 */
public final class SynchronousEventBus implements EventBus {

    private final Map<Class<?>, CopyOnWriteArrayList<Subscription<?>>> platformHandlers =
            new ConcurrentHashMap<>();
    private final Map<Class<?>, CopyOnWriteArrayList<Subscription<?>>> domainHandlers =
            new ConcurrentHashMap<>();

    @Override
    public void publish(PlatformEvent event) {
        dispatch(event, platformHandlers);
    }

    @Override
    public void publish(DomainEvent event) {
        dispatch(event, domainHandlers);
    }

    @Override
    public EventSubscription subscribePlatform(
            Class<? extends PlatformEvent> eventType, EventHandler<PlatformEvent> handler) {
        return registerHandler(eventType, handler, platformHandlers);
    }

    @Override
    public EventSubscription subscribeDomain(
            Class<? extends DomainEvent> eventType, EventHandler<DomainEvent> handler) {
        return registerHandler(eventType, handler, domainHandlers);
    }

    private <T> EventSubscription registerHandler(
            Class<? extends T> eventType,
            EventHandler<T> handler,
            Map<Class<?>, CopyOnWriteArrayList<Subscription<?>>> registry) {
        Subscription<T> subscription = new Subscription<>(handler);
        registry.computeIfAbsent(eventType, ignored -> new CopyOnWriteArrayList<>()).add(subscription);
        return subscription;
    }

    @SuppressWarnings("unchecked")
    private <T> void dispatch(T event, Map<Class<?>, CopyOnWriteArrayList<Subscription<?>>> registry) {
        Class<?> eventClass = event.getClass();
        for (Map.Entry<Class<?>, CopyOnWriteArrayList<Subscription<?>>> entry : registry.entrySet()) {
            if (entry.getKey().isAssignableFrom(eventClass)) {
                for (Subscription<?> subscription : List.copyOf(entry.getValue())) {
                    if (subscription.isActive()) {
                        ((Subscription<T>) subscription).deliver(event);
                    }
                }
            }
        }
    }

    private static final class Subscription<T> implements EventSubscription {

        private final EventHandler<T> handler;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private Subscription(EventHandler<T> handler) {
            this.handler = handler;
        }

        private void deliver(T event) {
            if (active.get()) {
                handler.handle(event);
            }
        }

        @Override
        public void unsubscribe() {
            active.set(false);
        }

        @Override
        public boolean isActive() {
            return active.get();
        }
    }
}
