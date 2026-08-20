package com.tmp.production.application.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderAcceptedIntoProductionTest {

    @Test
    void eventProperties() {
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Instant now = Instant.now();

        OrderAcceptedIntoProduction event =
                new OrderAcceptedIntoProduction(
                        UUID.randomUUID().toString(), now, orderId, 1, List.of(itemId));

        assertEquals("production.order.accepted", event.eventType());
        assertEquals("production", event.sourceCapabilityId());
        assertEquals(orderId, event.sourceOrderId());
        assertEquals(1, event.acceptedItemCount());
        assertEquals(List.of(itemId), event.sourceOrderItemIds());
        assertEquals(now, event.acceptedAt());
        assertNotNull(event.eventId());
    }
}
