package com.tmp.production.application.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tmp.production.domain.CancellationItemAction;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderProductionCancelledTest {

    private static final Instant T0 = Instant.parse("2026-08-24T10:00:00Z");

    @Test
    void validEventExposesContractFields() {
        UUID orderId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        OrderProductionCancelled event =
                new OrderProductionCancelled(
                        "evt-2",
                        T0,
                        docId,
                        orderId,
                        Optional.of("customer request"),
                        List.of(
                                new OrderProductionCancelled.ItemOutcome(
                                        itemId,
                                        UUID.randomUUID(),
                                        CancellationItemAction.CANCELLED_UNFINISHED)));

        assertEquals(T0, event.occurredAt());
        assertEquals(OrderProductionCancelled.EVENT_TYPE, event.eventType());
        assertEquals("production", event.sourceCapabilityId());
        assertEquals(docId, event.productionCancellationDocumentId());
        assertEquals(Optional.of("customer request"), event.reason());
    }
}
