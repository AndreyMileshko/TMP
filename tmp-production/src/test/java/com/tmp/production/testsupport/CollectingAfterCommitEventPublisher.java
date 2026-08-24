package com.tmp.production.testsupport;

import com.tmp.core.api.EventBus;
import com.tmp.core.api.event.DomainEvent;
import com.tmp.core.api.event.EventHandler;
import com.tmp.core.api.event.EventSubscription;
import com.tmp.core.api.event.PlatformEvent;
import com.tmp.document.TransactionAfterCommitEventPublisher;
import com.tmp.document.api.TransactionalEventPublisher;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Collects domain events delivered through real after-commit publication semantics. */
public final class CollectingAfterCommitEventPublisher {

    private final CopyOnWriteArrayList<DomainEvent> deliveredEvents = new CopyOnWriteArrayList<>();
    private final TransactionAfterCommitEventPublisher delegate;

    public CollectingAfterCommitEventPublisher() {
        delegate = new TransactionAfterCommitEventPublisher();
        delegate.setEventBus(
                new EventBus() {
                    @Override
                    public void publish(PlatformEvent event) {}

                    @Override
                    public void publish(DomainEvent event) {
                        deliveredEvents.add(event);
                    }

                    @Override
                    public EventSubscription subscribePlatform(
                            Class<? extends PlatformEvent> eventType,
                            EventHandler<PlatformEvent> handler) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public EventSubscription subscribeDomain(
                            Class<? extends DomainEvent> eventType,
                            EventHandler<DomainEvent> handler) {
                        throw new UnsupportedOperationException();
                    }
                });
    }

    public TransactionalEventPublisher publisher() {
        return delegate;
    }

    public List<DomainEvent> deliveredEvents() {
        return List.copyOf(deliveredEvents);
    }

    public void clearDelivered() {
        deliveredEvents.clear();
    }
}
