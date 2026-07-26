package com.tmp.document.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.core.api.EventBus;
import com.tmp.core.api.event.AbstractDomainEvent;
import com.tmp.core.api.event.DomainEvent;
import com.tmp.core.api.event.EventHandler;
import com.tmp.core.api.event.EventSubscription;
import com.tmp.core.api.event.PlatformEvent;
import com.tmp.document.TransactionAfterCommitEventPublisher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Contract tests for the public {@link TransactionalEventPublisher} (STAGE5-017).
 */
class TransactionalEventPublisherContractTest {

    private final List<DomainEvent> delivered = new ArrayList<>();
    private TransactionAfterCommitEventPublisher adapter;
    private TransactionalEventPublisher publisher;

    @BeforeEach
    void setUp() {
        delivered.clear();
        adapter = new TransactionAfterCommitEventPublisher();
        adapter.setEventBus(new CollectingEventBus(delivered));
        publisher = adapter;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publicInterfaceIsImplementedByInternalAdapterWithoutExposingItAsApiType() {
        assertInstanceOf(TransactionalEventPublisher.class, adapter);
        assertEquals("com.tmp.document.api", TransactionalEventPublisher.class.getPackageName());
        assertEquals(
                "com.tmp.document", TransactionAfterCommitEventPublisher.class.getPackageName());
    }

    @Test
    void eventIsNotDeliveredBeforeCommit() {
        publisher.publishAfterCommit(sampleEvent());
        assertTrue(delivered.isEmpty(), "Event must not be delivered before commit");
    }

    @Test
    void eventIsDeliveredAfterSuccessfulCommit() {
        DomainEvent event = sampleEvent();
        publisher.publishAfterCommit(event);

        assertTrue(delivered.isEmpty());
        List<TransactionSynchronization> syncs =
                List.copyOf(TransactionSynchronizationManager.getSynchronizations());
        syncs.forEach(TransactionSynchronization::afterCommit);

        assertEquals(1, delivered.size());
        assertEquals(event.eventId(), delivered.getFirst().eventId());
    }

    @Test
    void eventIsNotDeliveredAfterRollback() {
        publisher.publishAfterCommit(sampleEvent());
        List<TransactionSynchronization> syncs =
                List.copyOf(TransactionSynchronizationManager.getSynchronizations());
        syncs.forEach(
                sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertTrue(delivered.isEmpty(), "Event must not be delivered after rollback");
    }

    @Test
    void capabilityCanUseOnlyPublicInterface() {
        TransactionalEventPublisher onlyPublicApi = publisher;
        onlyPublicApi.publishAfterCommit(sampleEvent());
        List.copyOf(TransactionSynchronizationManager.getSynchronizations())
                .forEach(TransactionSynchronization::afterCommit);
        assertEquals(1, delivered.size());
    }

    private static DomainEvent sampleEvent() {
        return new SampleDomainEvent();
    }

    private static final class SampleDomainEvent extends AbstractDomainEvent {
        private SampleDomainEvent() {
            super(UUID.randomUUID().toString(), Instant.parse("2026-07-25T12:00:00Z"), "order.test");
        }
    }

    private static final class CollectingEventBus implements EventBus {
        private final List<DomainEvent> sink;

        private CollectingEventBus(List<DomainEvent> sink) {
            this.sink = sink;
        }

        @Override
        public void publish(PlatformEvent event) {
            // unused in these tests
        }

        @Override
        public void publish(DomainEvent event) {
            sink.add(event);
        }

        @Override
        public EventSubscription subscribePlatform(
                Class<? extends PlatformEvent> eventType, EventHandler<PlatformEvent> handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EventSubscription subscribeDomain(
                Class<? extends DomainEvent> eventType, EventHandler<DomainEvent> handler) {
            throw new UnsupportedOperationException();
        }
    }
}
