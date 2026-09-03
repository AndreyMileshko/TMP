package com.tmp.ui.shell.order.worklist;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tmp.order.api.OrderItemStatus;
import com.tmp.order.api.OrderStatus;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateStatus;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateView;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderItemOperationalStatusDeriverTest {

    @Test
    void draftParentIsEditing() {
        assertEquals(
                OrderItemOperationalStatus.EDITING,
                OrderItemOperationalStatusDeriver.derive(
                        OrderStatus.DRAFT, OrderItemStatus.DRAFT, Optional.empty()));
    }

    @Test
    void approvedParentIsEditing() {
        assertEquals(
                OrderItemOperationalStatus.EDITING,
                OrderItemOperationalStatusDeriver.derive(
                        OrderStatus.APPROVED, OrderItemStatus.ACTIVE, Optional.empty()));
    }

    @Test
    void cancelledItemIsCancelled() {
        assertEquals(
                OrderItemOperationalStatus.CANCELLED,
                OrderItemOperationalStatusDeriver.derive(
                        OrderStatus.DRAFT, OrderItemStatus.CANCELLED, Optional.empty()));
    }

    @Test
    void activeParentWithoutFactsIsUnavailable() {
        assertEquals(
                OrderItemOperationalStatus.STATUS_UNAVAILABLE,
                OrderItemOperationalStatusDeriver.derive(
                        OrderStatus.ACTIVE, OrderItemStatus.ACTIVE, Optional.empty()));
    }

    @Test
    void transferredZeroReleasedIsAwaiting() {
        assertEquals(
                OrderItemOperationalStatus.AWAITING_PRODUCTION,
                OrderItemOperationalStatusDeriver.derive(
                        OrderStatus.ACTIVE,
                        OrderItemStatus.ACTIVE,
                        Optional.of(facts(10, 0, ItemProductionStateStatus.IN_PRODUCTION))));
    }

    @Test
    void partialReleasedIsInProduction() {
        assertEquals(
                OrderItemOperationalStatus.IN_PRODUCTION,
                OrderItemOperationalStatusDeriver.derive(
                        OrderStatus.ACTIVE,
                        OrderItemStatus.ACTIVE,
                        Optional.of(facts(10, 4, ItemProductionStateStatus.PARTIALLY_RELEASED))));
    }

    @Test
    void fullyReleasedIsCompleted() {
        assertEquals(
                OrderItemOperationalStatus.COMPLETED,
                OrderItemOperationalStatusDeriver.derive(
                        OrderStatus.ACTIVE,
                        OrderItemStatus.ACTIVE,
                        Optional.of(facts(10, 10, ItemProductionStateStatus.RELEASED))));
    }

    @Test
    void cancelledWithReleasedIsPartiallyCompleted() {
        assertEquals(
                OrderItemOperationalStatus.PARTIALLY_COMPLETED,
                OrderItemOperationalStatusDeriver.derive(
                        OrderStatus.ACTIVE,
                        OrderItemStatus.ACTIVE,
                        Optional.of(facts(10, 3, ItemProductionStateStatus.CANCELLED))));
    }

    @Test
    void cancelledWithZeroReleasedIsCancelled() {
        assertEquals(
                OrderItemOperationalStatus.CANCELLED,
                OrderItemOperationalStatusDeriver.derive(
                        OrderStatus.ACTIVE,
                        OrderItemStatus.ACTIVE,
                        Optional.of(facts(10, 0, ItemProductionStateStatus.CANCELLED))));
    }

    private static ItemProductionStateView facts(
            long ordered, long released, ItemProductionStateStatus status) {
        return new ItemProductionStateView(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                status,
                ordered,
                ordered,
                Math.max(0L, ordered - released),
                released,
                Optional.empty(),
                Instant.parse("2026-01-01T00:00:00Z"),
                List.of());
    }
}
