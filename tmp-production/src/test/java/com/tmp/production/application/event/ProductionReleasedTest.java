package com.tmp.production.application.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tmp.production.domain.ProductionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductionReleasedTest {

    private static final Instant T0 = Instant.parse("2026-08-24T10:00:00Z");

    @Test
    void validEventExposesContractFields() {
        UUID orderId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID specId = UUID.randomUUID();

        ProductionReleased event =
                new ProductionReleased(
                        "evt-1",
                        T0,
                        docId,
                        orderId,
                        List.of(
                                new ProductionReleased.ReleasedItem(
                                        itemId, specId, 3, ProductionStatus.PARTIALLY_RELEASED)));

        assertEquals(T0, event.occurredAt());
        assertEquals(ProductionReleased.EVENT_TYPE, event.eventType());
        assertEquals("production", event.sourceCapabilityId());
        assertEquals(docId, event.productionReleaseDocumentId());
        assertEquals(1, event.releasedItems().size());
    }

    @Test
    void duplicateItemIdsRejected() {
        UUID itemId = UUID.randomUUID();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ProductionReleased(
                                "evt-1",
                                T0,
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                List.of(
                                        new ProductionReleased.ReleasedItem(
                                                itemId,
                                                UUID.randomUUID(),
                                                1,
                                                ProductionStatus.RELEASED),
                                        new ProductionReleased.ReleasedItem(
                                                itemId,
                                                UUID.randomUUID(),
                                                2,
                                                ProductionStatus.RELEASED))));
    }
}
