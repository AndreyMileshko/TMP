package com.tmp.order.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderItemIdTest {

    @Test
    void ofRejectsNull() {
        assertThrows(NullPointerException.class, () -> OrderItemId.of(null));
    }

    @Test
    void ofPreservesValue() {
        UUID value = UUID.randomUUID();
        assertEquals(value, OrderItemId.of(value).value());
    }

    @Test
    void generateProducesNonNullUniqueIds() {
        OrderItemId first = OrderItemId.generate();
        OrderItemId second = OrderItemId.generate();
        assertNotNull(first.value());
        assertNotEquals(first, second);
    }

    @Test
    void equalsAndHashCodeBasedOnValue() {
        UUID value = UUID.randomUUID();
        assertEquals(OrderItemId.of(value), OrderItemId.of(value));
        assertEquals(OrderItemId.of(value).hashCode(), OrderItemId.of(value).hashCode());
    }
}
