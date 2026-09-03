package com.tmp.order.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OrderStatusesTest {

    @Test
    void orderStatusHasExactlyStage5Values() {
        assertArrayEquals(
                new OrderStatus[] {
                    OrderStatus.DRAFT, OrderStatus.APPROVED, OrderStatus.ACTIVE, OrderStatus.CANCELLED
                },
                OrderStatus.values());
    }

    @Test
    void orderStatusDoesNotContainProductionDerivedValues() {
        Set<String> names =
                Arrays.stream(OrderStatus.values()).map(Enum::name).collect(Collectors.toSet());
        assertFalse(names.contains("IN_PRODUCTION"));
        assertFalse(names.contains("COMPLETED"));
        assertFalse(names.contains("PARTIALLY_COMPLETED"));
        assertFalse(names.contains("AWAITING_PRODUCTION"));
        assertFalse(names.contains("EDITING"));
    }

    @Test
    void orderItemStatusHasExactlyStage5Values() {
        assertArrayEquals(
                new OrderItemStatus[] {
                    OrderItemStatus.DRAFT, OrderItemStatus.ACTIVE, OrderItemStatus.CANCELLED
                },
                OrderItemStatus.values());
    }

    @Test
    void revisionStatusHasExactlyStage5Values() {
        assertArrayEquals(
                new RevisionStatus[] {RevisionStatus.DRAFT, RevisionStatus.ACTIVE},
                RevisionStatus.values());
    }
}
