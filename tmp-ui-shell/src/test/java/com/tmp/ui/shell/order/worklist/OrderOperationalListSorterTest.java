package com.tmp.ui.shell.order.worklist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderOperationalListSorterTest {

    @Test
    void statusAscUsesExplicitBusinessRank() {
        List<OrderOperationalSummary> rows = new ArrayList<>();
        rows.add(row("done", OrderOperationalStatus.COMPLETED, Instant.parse("2026-09-01T10:00:00Z"), 1));
        rows.add(row("edit", OrderOperationalStatus.EDITING, Instant.parse("2026-09-01T11:00:00Z"), 1));
        rows.add(row("cancel", OrderOperationalStatus.CANCELLED, Instant.parse("2026-09-01T12:00:00Z"), 1));
        rows.add(row("prod", OrderOperationalStatus.IN_PRODUCTION, Instant.parse("2026-09-01T13:00:00Z"), 1));
        rows.add(row("await", OrderOperationalStatus.AWAITING_PRODUCTION, Instant.parse("2026-09-01T14:00:00Z"), 1));
        rows.add(row("partial", OrderOperationalStatus.PARTIALLY_COMPLETED, Instant.parse("2026-09-01T15:00:00Z"), 1));
        OrderOperationalListSorter.sortInPlace(
                rows, OrderListSortField.STATUS, OrderListSortDirection.ASC);
        assertEquals(
                List.of(
                        OrderOperationalStatus.EDITING,
                        OrderOperationalStatus.AWAITING_PRODUCTION,
                        OrderOperationalStatus.IN_PRODUCTION,
                        OrderOperationalStatus.PARTIALLY_COMPLETED,
                        OrderOperationalStatus.COMPLETED,
                        OrderOperationalStatus.CANCELLED),
                rows.stream().map(OrderOperationalSummary::operationalStatus).toList());
        OrderOperationalListSorter.sortInPlace(
                rows, OrderListSortField.STATUS, OrderListSortDirection.DESC);
        assertEquals(OrderOperationalStatus.CANCELLED, rows.getFirst().operationalStatus());
        assertEquals(OrderOperationalStatus.EDITING, rows.getLast().operationalStatus());
    }

    @Test
    void createdAtSortsByInstantNotFormattedString() {
        List<OrderOperationalSummary> rows = new ArrayList<>();
        rows.add(row("a", OrderOperationalStatus.EDITING, Instant.parse("2026-08-31T10:00:00Z"), 1));
        rows.add(row("b", OrderOperationalStatus.EDITING, Instant.parse("2026-09-01T10:00:00Z"), 1));
        rows.add(row("c", OrderOperationalStatus.EDITING, Instant.parse("2026-07-02T10:00:00Z"), 1));
        OrderOperationalListSorter.sortInPlace(
                rows, OrderListSortField.CREATED_AT, OrderListSortDirection.ASC);
        assertEquals("c", rows.get(0).orderNumber());
        assertEquals("a", rows.get(1).orderNumber());
        assertEquals("b", rows.get(2).orderNumber());
    }

    @Test
    void itemCountSortsNumerically() {
        List<OrderOperationalSummary> rows = new ArrayList<>();
        rows.add(row("x", OrderOperationalStatus.EDITING, Instant.parse("2026-09-01T10:00:00Z"), 115));
        rows.add(row("y", OrderOperationalStatus.EDITING, Instant.parse("2026-09-01T11:00:00Z"), 2));
        rows.add(row("z", OrderOperationalStatus.EDITING, Instant.parse("2026-09-01T12:00:00Z"), 10));
        OrderOperationalListSorter.sortInPlace(
                rows, OrderListSortField.ITEM_COUNT, OrderListSortDirection.ASC);
        assertEquals(List.of(2L, 10L, 115L), rows.stream().map(OrderOperationalSummary::itemQuantity).toList());
    }

    @Test
    void orderNumberUsesNaturalOrdering() {
        assertTrue(OrderOperationalListSorter.compareNatural("2", "10") < 0);
        assertTrue(OrderOperationalListSorter.compareNatural("TEST-2", "TEST-10") < 0);
        assertTrue(OrderOperationalListSorter.compareNatural("ABC-12", "ABC-2") > 0);
    }

    @Test
    void blankCustomerSortsLastAscending() {
        List<OrderOperationalSummary> rows = new ArrayList<>();
        rows.add(named("a", "Beta", Instant.parse("2026-09-01T10:00:00Z")));
        rows.add(named("b", null, Instant.parse("2026-09-01T11:00:00Z")));
        rows.add(named("c", "alpha", Instant.parse("2026-09-01T12:00:00Z")));
        OrderOperationalListSorter.sortInPlace(
                rows, OrderListSortField.CUSTOMER, OrderListSortDirection.ASC);
        assertEquals("c", rows.get(0).orderNumber());
        assertEquals("a", rows.get(1).orderNumber());
        assertEquals("b", rows.get(2).orderNumber());
    }

    private static OrderOperationalSummary row(
            String number, OrderOperationalStatus status, Instant createdAt, long qty) {
        return new OrderOperationalSummary(
                OrderId.of(UUID.nameUUIDFromBytes(number.getBytes())),
                number,
                "c",
                "Customer",
                createdAt,
                qty,
                OrderStatus.DRAFT,
                status);
    }

    private static OrderOperationalSummary named(String number, String customer, Instant createdAt) {
        return new OrderOperationalSummary(
                OrderId.of(UUID.nameUUIDFromBytes(number.getBytes())),
                number,
                "c",
                customer,
                createdAt,
                1L,
                OrderStatus.DRAFT,
                OrderOperationalStatus.EDITING);
    }
}
