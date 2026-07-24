package com.tmp.order.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PayloadRevisionTest {

    @ParameterizedTest
    @ValueSource(longs = {-1L, -42L, Long.MIN_VALUE})
    void ofRejectsNegativeValues(long invalid) {
        assertThrows(IllegalArgumentException.class, () -> PayloadRevision.of(invalid));
    }

    @Test
    void initialIsZero() {
        assertEquals(0L, PayloadRevision.initial().value());
    }

    @Test
    void nextIncrementsByOne() {
        assertEquals(1L, PayloadRevision.initial().next().value());
        assertEquals(6L, PayloadRevision.of(5L).next().value());
    }

    @Test
    void ofAcceptsZeroAndPositive() {
        assertEquals(0L, PayloadRevision.of(0L).value());
        assertEquals(9L, PayloadRevision.of(9L).value());
    }

    @Test
    void equalsAndHashCodeBasedOnValue() {
        assertEquals(PayloadRevision.of(4L), PayloadRevision.of(4L));
        assertEquals(PayloadRevision.of(4L).hashCode(), PayloadRevision.of(4L).hashCode());
    }

    @Test
    void toStringIsPlainNumber() {
        assertEquals("4", PayloadRevision.of(4L).toString());
    }
}
