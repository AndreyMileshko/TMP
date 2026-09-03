package com.tmp.ui.shell.order.worklist;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tmp.order.api.OrderStatus;
import com.tmp.production.api.ProductionQueryApi.OrderProductionListFacts;
import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderOperationalStatusDeriverTest {

    @Test
    void draftAndApprovedAreEditing() {
        assertEquals(
                OrderOperationalStatus.EDITING,
                OrderOperationalStatusDeriver.derive(OrderStatus.DRAFT, 10L, empty()));
        assertEquals(
                OrderOperationalStatus.EDITING,
                OrderOperationalStatusDeriver.derive(OrderStatus.APPROVED, 10L, empty()));
    }

    @Test
    void activeWithMissingProductionFactsIsUnavailable() {
        assertEquals(
                OrderOperationalStatus.STATUS_UNAVAILABLE,
                OrderOperationalStatusDeriver.derive(
                        OrderStatus.ACTIVE, 10L, java.util.Optional.empty()));
    }

    @Test
    void activeWithZeroReleasedIsAwaitingProduction() {
        assertEquals(
                OrderOperationalStatus.AWAITING_PRODUCTION,
                OrderOperationalStatusDeriver.derive(OrderStatus.ACTIVE, 10L, empty()));
        assertEquals(
                OrderOperationalStatus.AWAITING_PRODUCTION,
                OrderOperationalStatusDeriver.derive(
                        OrderStatus.ACTIVE,
                        10L,
                        facts(OrderProductionViewStatus.IN_PRODUCTION, 10L, 0L, 10L, false)));
    }

    @Test
    void partialReleasedWithWorkRemainingIsInProduction() {
        assertEquals(
                OrderOperationalStatus.IN_PRODUCTION,
                OrderOperationalStatusDeriver.derive(
                        OrderStatus.ACTIVE,
                        10L,
                        facts(OrderProductionViewStatus.IN_PRODUCTION, 10L, 8L, 2L, false)));
    }

    @Test
    void allManufacturedIsCompleted() {
        assertEquals(
                OrderOperationalStatus.COMPLETED,
                OrderOperationalStatusDeriver.derive(
                        OrderStatus.ACTIVE,
                        10L,
                        facts(OrderProductionViewStatus.MANUFACTURED, 10L, 10L, 0L, false)));
    }

    @Test
    void cancelledBeforeManufactureIsCancelled() {
        assertEquals(
                OrderOperationalStatus.CANCELLED,
                OrderOperationalStatusDeriver.derive(OrderStatus.CANCELLED, 10L, empty()));
        assertEquals(
                OrderOperationalStatus.CANCELLED,
                OrderOperationalStatusDeriver.derive(
                        OrderStatus.ACTIVE,
                        10L,
                        facts(OrderProductionViewStatus.CANCELLED, 10L, 0L, 0L, true)));
    }

    @Test
    void partialManufacturePlusCancelledRemainderIsPartiallyCompleted() {
        assertEquals(
                OrderOperationalStatus.PARTIALLY_COMPLETED,
                OrderOperationalStatusDeriver.derive(
                        OrderStatus.ACTIVE,
                        10L,
                        facts(OrderProductionViewStatus.CANCELLED, 10L, 8L, 0L, true)));
    }

    private static OrderProductionListFacts empty() {
        return facts(OrderProductionViewStatus.NOT_ACCEPTED, 0L, 0L, 0L, false);
    }

    private static OrderProductionListFacts facts(
            OrderProductionViewStatus status,
            long ordered,
            long released,
            long active,
            boolean cancellationPosted) {
        return new OrderProductionListFacts(
                UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
                status,
                ordered,
                released,
                active,
                cancellationPosted);
    }
}
