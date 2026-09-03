package com.tmp.ui.shell.order.worklist;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tmp.order.api.OrderItemStatus;
import com.tmp.order.api.OrderStatus;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateStatus;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderItemOperationalStatusDeriverTest {

    @Test
    void draftParentIsEditing() {
        assertEquals(
                OrderItemOperationalStatus.EDITING,
                OrderItemOperationalStatusDeriver.derive(
                        OrderStatus.DRAFT,
                        OrderItemStatus.DRAFT,
                        ItemProductionReadResult.successNotAccepted()));
    }

    @Test
    void approvedParentNonEditableItemIsReadyForTransfer() {
        assertEquals(
                OrderItemOperationalStatus.READY_FOR_TRANSFER,
                OrderItemOperationalStatusDeriver.derive(
                        OrderStatus.APPROVED,
                        OrderItemStatus.ACTIVE,
                        ItemProductionReadResult.successNotAccepted()));
    }

    @Test
    void draftParentActiveItemIsReadyForTransfer() {
        assertEquals(
                OrderItemOperationalStatus.READY_FOR_TRANSFER,
                OrderItemOperationalStatusDeriver.derive(
                        OrderStatus.DRAFT,
                        OrderItemStatus.ACTIVE,
                        ItemProductionReadResult.successNotAccepted()));
    }

    @Test
    void cancelledItemIsCancelled() {
        assertEquals(
                OrderItemOperationalStatus.CANCELLED,
                OrderItemOperationalStatusDeriver.derive(
                        OrderStatus.DRAFT,
                        OrderItemStatus.CANCELLED,
                        ItemProductionReadResult.successNotAccepted()));
    }

    @Test
    void activeParentSuccessfulEmptyIsAwaitingProduction() {
        assertEquals(
                OrderItemOperationalStatus.AWAITING_PRODUCTION,
                OrderItemOperationalStatusDeriver.derive(
                        OrderStatus.ACTIVE,
                        OrderItemStatus.ACTIVE,
                        ItemProductionReadResult.successNotAccepted()));
    }

    @Test
    void activeParentUnavailableIsStatusUnavailable() {
        assertEquals(
                OrderItemOperationalStatus.STATUS_UNAVAILABLE,
                OrderItemOperationalStatusDeriver.derive(
                        OrderStatus.ACTIVE,
                        OrderItemStatus.ACTIVE,
                        ItemProductionReadResult.unavailable()));
    }

    @Test
    void transferredZeroReleasedIsAwaiting() {
        assertEquals(
                OrderItemOperationalStatus.AWAITING_PRODUCTION,
                OrderItemOperationalStatusDeriver.derive(
                        OrderStatus.ACTIVE,
                        OrderItemStatus.ACTIVE,
                        ItemProductionReadResult.successWithState(
                                facts(10, 0, ItemProductionStateStatus.IN_PRODUCTION))));
    }

    @Test
    void partialReleasedIsInProduction() {
        assertEquals(
                OrderItemOperationalStatus.IN_PRODUCTION,
                OrderItemOperationalStatusDeriver.derive(
                        OrderStatus.ACTIVE,
                        OrderItemStatus.ACTIVE,
                        ItemProductionReadResult.successWithState(
                                facts(10, 4, ItemProductionStateStatus.PARTIALLY_RELEASED))));
    }

    @Test
    void fullyReleasedIsCompleted() {
        assertEquals(
                OrderItemOperationalStatus.COMPLETED,
                OrderItemOperationalStatusDeriver.derive(
                        OrderStatus.ACTIVE,
                        OrderItemStatus.ACTIVE,
                        ItemProductionReadResult.successWithState(
                                facts(10, 10, ItemProductionStateStatus.RELEASED))));
    }

    @Test
    void cancelledWithReleasedIsPartiallyCompleted() {
        assertEquals(
                OrderItemOperationalStatus.PARTIALLY_COMPLETED,
                OrderItemOperationalStatusDeriver.derive(
                        OrderStatus.ACTIVE,
                        OrderItemStatus.ACTIVE,
                        ItemProductionReadResult.successWithState(
                                facts(10, 3, ItemProductionStateStatus.CANCELLED))));
    }

    @Test
    void cancelledWithZeroReleasedIsCancelled() {
        assertEquals(
                OrderItemOperationalStatus.CANCELLED,
                OrderItemOperationalStatusDeriver.derive(
                        OrderStatus.ACTIVE,
                        OrderItemStatus.ACTIVE,
                        ItemProductionReadResult.successWithState(
                                facts(10, 0, ItemProductionStateStatus.CANCELLED))));
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
                java.util.Optional.empty(),
                Instant.parse("2026-01-01T00:00:00Z"),
                List.of());
    }
}
