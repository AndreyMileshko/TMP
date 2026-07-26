package com.tmp.document.api;

import com.tmp.core.api.event.DomainEvent;

/**
 * Public after-commit domain event publication contract (Document Engine Specification v1.1).
 *
 * <p>Capabilities publish business {@link DomainEvent}s through this interface only. Events are
 * delivered after a successful transaction commit and are not delivered when the surrounding
 * transaction rolls back. Implementations live inside Document Engine; callers must not depend on
 * internal adapter types.
 */
public interface TransactionalEventPublisher {

    /**
     * Schedules {@code event} for delivery after the current transaction commits successfully.
     *
     * <p>If no transaction synchronization is active, delivery may occur immediately (best-effort
     * for non-transactional call sites). On rollback, a previously scheduled event is not
     * delivered.
     *
     * @param event domain event to publish after commit; must not be {@code null}
     */
    void publishAfterCommit(DomainEvent event);
}
