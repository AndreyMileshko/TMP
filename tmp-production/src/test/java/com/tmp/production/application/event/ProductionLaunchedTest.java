package com.tmp.production.application.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductionLaunchedTest {

    @Test
    void eventProperties() {
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID specId = UUID.randomUUID();
        Instant now = Instant.now();

        ProductionLaunched event =
                new ProductionLaunched(
                        UUID.randomUUID().toString(), now, orderId, itemId, specId, 10);

        assertEquals("production.launched", event.eventType());
        assertEquals("production", event.sourceCapabilityId());
        assertEquals(orderId, event.sourceOrderId());
        assertEquals(itemId, event.sourceOrderItemId());
        assertEquals(specId, event.specificationId());
        assertEquals(10, event.orderedQuantity());
        assertEquals(now, event.occurredAt());
        assertNotNull(event.eventId());
    }
}
