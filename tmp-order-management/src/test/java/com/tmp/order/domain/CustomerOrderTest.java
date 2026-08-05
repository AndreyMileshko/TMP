package com.tmp.order.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CustomerOrderTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-24T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void createStartsInDraft() {
        CustomerOrder order = sampleOrder();
        assertEquals(OrderStatus.DRAFT, order.status());
        assertEquals(0L, order.version());
        assertTrue(order.isDraft());
    }

    @Test
    void approveFromDraftSucceeds() {
        CustomerOrder approved = sampleOrder().approve(CLOCK);
        assertEquals(OrderStatus.APPROVED, approved.status());
        assertTrue(approved.isApproved());
    }

    @Test
    void activateFromApprovedSucceeds() {
        CustomerOrder active = sampleOrder().approve(CLOCK).activate(CLOCK);
        assertEquals(OrderStatus.ACTIVE, active.status());
        assertTrue(active.isActive());
    }

    @Test
    void activateFromDraftIsForbidden() {
        CustomerOrder draft = sampleOrder();
        InvalidOrderStateException ex =
                assertThrows(InvalidOrderStateException.class, () -> draft.activate(CLOCK));
        assertTrue(ex.getMessage().contains("APPROVED"));
    }

    @Test
    void cancelFromDraftSucceeds() {
        CustomerOrder cancelled = sampleOrder().cancel(CLOCK);
        assertEquals(OrderStatus.CANCELLED, cancelled.status());
        assertTrue(cancelled.isCancelled());
    }

    @Test
    void updateCommercialDataAllowedInDraft() {
        CustomerOrder order = sampleOrder();
        OrderCommercialData updated = OrderCommercialData.of(
                "CUST-2",
                "New Customer",
                "CTR-2",
                "SITE-2",
                "Manager B",
                OrderDirection.DEALER,
                CurrencyCode.of("USD"));
        CustomerOrder result = order.updateCommercialData(updated, CLOCK);
        assertEquals("New Customer", result.commercialData().customerName());
        assertEquals(OrderDirection.DEALER, result.commercialData().direction());
        assertEquals(OrderStatus.DRAFT, result.status());
    }

    @Test
    void reApprovalForbidden() {
        CustomerOrder approved = sampleOrder().approve(CLOCK);
        InvalidOrderStateException ex =
                assertThrows(InvalidOrderStateException.class, () -> approved.approve(CLOCK));
        assertTrue(ex.getMessage().contains("already approved"));
    }

    @Test
    void approvedAndActiveCannotBeCancelled() {
        CustomerOrder approved = sampleOrder().approve(CLOCK);
        InvalidOrderStateException approvedEx =
                assertThrows(InvalidOrderStateException.class, () -> approved.cancel(CLOCK));
        assertTrue(approvedEx.getMessage().contains("cannot be cancelled"));

        CustomerOrder active = sampleOrder().approve(CLOCK).activate(CLOCK);
        InvalidOrderStateException activeEx =
                assertThrows(InvalidOrderStateException.class, () -> active.cancel(CLOCK));
        assertTrue(activeEx.getMessage().contains("cannot be cancelled"));
    }

    @Test
    void cancelledCannotBeApproved() {
        CustomerOrder cancelled = sampleOrder().cancel(CLOCK);
        assertThrows(InvalidOrderStateException.class, () -> cancelled.approve(CLOCK));
    }

    @Test
    void cancelledCannotBeCancelledAgain() {
        CustomerOrder cancelled = sampleOrder().cancel(CLOCK);
        assertThrows(InvalidOrderStateException.class, () -> cancelled.cancel(CLOCK));
    }

    @Test
    void commercialUpdateForbiddenWhenApproved() {
        CustomerOrder approved = sampleOrder().approve(CLOCK);
        OrderCommercialData data = sampleCommercialData();
        assertThrows(
                InvalidOrderStateException.class,
                () -> approved.updateCommercialData(data, CLOCK));
    }

    @Test
    void commercialUpdateForbiddenWhenCancelled() {
        CustomerOrder cancelled = sampleOrder().cancel(CLOCK);
        assertThrows(
                InvalidOrderStateException.class,
                () -> cancelled.updateCommercialData(sampleCommercialData(), CLOCK));
    }

    @Test
    void identityAndOrderNumberAreImmutableAcrossTransitions() {
        CustomerOrder order = sampleOrder();
        OrderId id = order.id();
        var orderNumber = order.orderNumber();

        CustomerOrder updated = order.updateCommercialData(sampleCommercialData(), CLOCK);
        assertEquals(id, updated.id());
        assertEquals(orderNumber, updated.orderNumber());

        CustomerOrder approved = order.approve(CLOCK);
        assertEquals(id, approved.id());
        assertEquals(orderNumber, approved.orderNumber());

        CustomerOrder cancelled = order.cancel(CLOCK);
        assertEquals(id, cancelled.id());
        assertEquals(orderNumber, cancelled.orderNumber());
    }

    @Test
    void optimisticVersionIsPreservedByDomainStateChanges() {
        CustomerOrder base = sampleOrder();
        CustomerOrder rehydrated =
                CustomerOrder.rehydrate(
                        base.id(),
                        base.orderNumber(),
                        base.commercialData(),
                        OrderStatus.DRAFT,
                        7L,
                        CLOCK.instant(),
                        CLOCK.instant());
        assertEquals(7L, rehydrated.version());

        CustomerOrder updated = rehydrated.updateCommercialData(sampleCommercialData(), CLOCK);
        assertEquals(7L, updated.version());

        CustomerOrder approved = rehydrated.approve(CLOCK);
        assertEquals(7L, approved.version());

        CustomerOrder cancelled = rehydrated.cancel(CLOCK);
        assertEquals(7L, cancelled.version());
    }

    @Test
    void orderNumberRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> OrderNumber.of("  "));
        assertThrows(NullPointerException.class, () -> OrderNumber.of(null));
    }

    private static CustomerOrder sampleOrder() {
        return CustomerOrder.create(
                OrderId.generate(),
                OrderNumber.of("ORD-001"),
                sampleCommercialData(),
                CLOCK);
    }

    private static OrderCommercialData sampleCommercialData() {
        return OrderCommercialData.of(
                "CUST-1",
                "Acme LLC",
                "CTR-1",
                "SITE-1",
                "Manager A",
                OrderDirection.PRIVATE,
                CurrencyCode.of("RUB"));
    }
}
