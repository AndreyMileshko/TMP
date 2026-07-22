package com.tmp.document;

import com.tmp.core.api.EventBus;
import com.tmp.core.api.event.DomainEvent;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes domain events only after the surrounding transaction commits successfully.
 */
public final class TransactionAfterCommitEventPublisher {

    private EventBus eventBus;

    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void publishAfterCommit(DomainEvent event) {
        if (eventBus == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eventBus.publish(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventBus.publish(event);
            }
        });
    }
}
