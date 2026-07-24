package com.tmp.order.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderIdTest {

    @Test
    void ofRejectsNull() {
        assertThrows(NullPointerException.class, () -> OrderId.of(null));
    }

    @Test
    void ofPreservesValue() {
        UUID value = UUID.randomUUID();
        assertEquals(value, OrderId.of(value).value());
    }

    @Test
    void generateProducesNonNullUniqueIds() {
        OrderId first = OrderId.generate();
        OrderId second = OrderId.generate();
        assertNotNull(first.value());
        assertNotEquals(first, second);
    }

    @Test
    void equalsAndHashCodeBasedOnValue() {
        UUID value = UUID.randomUUID();
        OrderId a = OrderId.of(value);
        OrderId b = OrderId.of(value);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertSame(a, a);
    }

    @Test
    void toStringIsUuidString() {
        UUID value = UUID.randomUUID();
        assertTrue(OrderId.of(value).toString().equals(value.toString()));
    }
}
