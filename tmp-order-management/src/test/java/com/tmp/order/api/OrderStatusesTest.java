package com.tmp.order.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class OrderStatusesTest {

    @Test
    void orderStatusHasExactlyStage5Values() {
        assertArrayEquals(
                new OrderStatus[] {OrderStatus.DRAFT, OrderStatus.APPROVED, OrderStatus.CANCELLED},
                OrderStatus.values());
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
                new RevisionStatus[] {RevisionStatus.DRAFT, RevisionStatus.APPROVED},
                RevisionStatus.values());
    }
}
