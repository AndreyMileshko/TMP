package com.tmp.core.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tmp.core.api.event.DomainEvent;
import com.tmp.core.api.event.PlatformEvent;
import java.util.ArrayList;
import java.util.List;
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

    private record TestDomainEvent(String sourceCapabilityId) implements DomainEvent {
    }
}
