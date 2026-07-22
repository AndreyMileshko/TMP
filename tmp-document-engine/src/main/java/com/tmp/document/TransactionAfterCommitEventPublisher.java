package com.tmp.document;

import com.tmp.core.api.EventBus;
import com.tmp.core.api.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes domain events only after the surrounding transaction commits successfully.
 *
 * <h2>After-commit handler failure policy</h2>
 *
 * Delivery is best-effort relative to the already-committed document operation:
 * <ul>
 *   <li>Document mutations commit independently of subscriber success.</li>
 *   <li>If a subscriber throws after commit, the failure is logged and swallowed so the
 *       document API does not appear to have rolled back.</li>
 *   <li>Callers must not treat after-commit delivery failures as a signal to retry
 *       document create/post/unpost/close/delete (that would risk duplicates).</li>
 *   <li>Platform Core {@link EventBus} failure propagation is unchanged; this policy is
 *       local to Document Engine post-commit publication.</li>
 * </ul>
 */
public final class TransactionAfterCommitEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionAfterCommitEventPublisher.class);

    private EventBus eventBus;

    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void publishAfterCommit(DomainEvent event) {
        if (eventBus == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deliverSafely(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deliverSafely(event);
            }
        });
    }

    private void deliverSafely(DomainEvent event) {
        try {
            eventBus.publish(event);
        } catch (RuntimeException deliveryFailure) {
            LOG.error(
                    "Post-commit domain event delivery failed after successful commit: eventType={}, eventId={}",
                    event.getClass().getName(),
                    event.eventId(),
                    deliveryFailure);
        }
    }
}
