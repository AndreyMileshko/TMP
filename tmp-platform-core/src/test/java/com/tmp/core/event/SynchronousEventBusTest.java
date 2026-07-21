package com.tmp.core.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.core.api.event.AbstractDomainEvent;
import com.tmp.core.api.event.AbstractPlatformEvent;
import com.tmp.core.api.event.DomainEvent;
import com.tmp.core.api.event.PlatformEvent;
import com.tmp.core.api.event.platform.PlatformStartedEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SynchronousEventBusTest {

    @Test
    void publishesPlatformAndDomainEventsSynchronously() {
        SynchronousEventBus eventBus = new SynchronousEventBus();
        List<String> received = new ArrayList<>();

        eventBus.subscribePlatform(PlatformStartedEvent.class, event -> received.add("platform"));
        eventBus.subscribeDomain(TestDomainEvent.class, event -> received.add("domain:" + event.sourceCapabilityId()));

        eventBus.publish(new PlatformStartedEvent());
        eventBus.publish(new TestDomainEvent("cap.test"));

        assertEquals(List.of("platform", "domain:cap.test"), received);
    }

    @Test
    void repeatedEventMetadataCallsReturnSameValues() {
        PlatformStartedEvent event = new PlatformStartedEvent("event-1", Instant.parse("2026-07-20T10:00:00Z"));

        assertEquals("event-1", event.eventId());
        assertEquals("event-1", event.eventId());
        assertEquals(Instant.parse("2026-07-20T10:00:00Z"), event.occurredAt());
        assertEquals(Instant.parse("2026-07-20T10:00:00Z"), event.occurredAt());
    }

    @Test
    void differentEventsHaveDifferentIds() {
        PlatformStartedEvent first = new PlatformStartedEvent();
        PlatformStartedEvent second = new PlatformStartedEvent();

        assertNotEquals(first.eventId(), second.eventId());
    }

    @Test
    void eventMetadataSurvivesEventBusDeliveryUnchanged() {
        SynchronousEventBus eventBus = new SynchronousEventBus();
        PlatformStartedEvent published = new PlatformStartedEvent("stable-id", Instant.parse("2026-07-20T11:00:00Z"));
        AtomicReference<PlatformEvent> delivered = new AtomicReference<>();

        eventBus.subscribePlatform(PlatformStartedEvent.class, delivered::set);
        eventBus.publish(published);

        PlatformEvent received = delivered.get();
        assertEquals("stable-id", received.eventId());
        assertEquals("stable-id", received.eventId());
        assertEquals(Instant.parse("2026-07-20T11:00:00Z"), received.occurredAt());
        assertEquals(Instant.parse("2026-07-20T11:00:00Z"), received.occurredAt());
    }

    @Test
    void unsubscribePreventsFurtherDelivery() {
        SynchronousEventBus eventBus = new SynchronousEventBus();
        AtomicBoolean received = new AtomicBoolean(false);

        var subscription = eventBus.subscribePlatform(PlatformStartedEvent.class, event -> received.set(true));
        subscription.unsubscribe();

        eventBus.publish(new PlatformStartedEvent());
        assertTrue(!received.get());
    }

    @Test
    void subscriptionBySupertypeReceivesConcreteEvents() {
        SynchronousEventBus eventBus = new SynchronousEventBus();
        AtomicBoolean received = new AtomicBoolean(false);

        eventBus.subscribePlatform(PlatformEvent.class, event -> received.set(true));
        eventBus.publish(new PlatformStartedEvent());

        assertTrue(received.get());
    }

    @Test
    void multipleHandlersReceiveSameEvent() {
        SynchronousEventBus eventBus = new SynchronousEventBus();
        List<String> received = new ArrayList<>();

        eventBus.subscribePlatform(PlatformStartedEvent.class, event -> received.add("first"));
        eventBus.subscribePlatform(PlatformStartedEvent.class, event -> received.add("second"));

        eventBus.publish(new PlatformStartedEvent());

        assertEquals(List.of("first", "second"), received);
    }

    @Test
    void handlerFailureStopsFurtherHandlersAndPropagatesException() {
        SynchronousEventBus eventBus = new SynchronousEventBus();
        List<String> received = new ArrayList<>();

        eventBus.subscribePlatform(PlatformStartedEvent.class, event -> {
            throw new IllegalStateException("handler failed");
        });
        eventBus.subscribePlatform(PlatformStartedEvent.class, event -> received.add("should-not-run"));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class, () -> eventBus.publish(new PlatformStartedEvent()));

        assertEquals("handler failed", failure.getMessage());
        assertTrue(received.isEmpty());
    }

    private static final class TestDomainEvent extends AbstractDomainEvent {

        private TestDomainEvent(String sourceCapabilityId) {
            super(sourceCapabilityId);
        }
    }
}
