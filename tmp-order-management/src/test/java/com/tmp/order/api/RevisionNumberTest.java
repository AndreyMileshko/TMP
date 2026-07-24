package com.tmp.order.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RevisionNumberTest {

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100, Integer.MIN_VALUE})
    void ofRejectsValuesBelowOne(int invalid) {
        assertThrows(IllegalArgumentException.class, () -> RevisionNumber.of(invalid));
    }

    @Test
    void ofAcceptsValuesFromOne() {
        assertEquals(1, RevisionNumber.of(1).value());
        assertEquals(7, RevisionNumber.of(7).value());
    }

    @Test
    void firstIsOne() {
        assertEquals(1, RevisionNumber.first().value());
    }

    @Test
    void nextIncreasesMonotonically() {
        RevisionNumber first = RevisionNumber.first();
        RevisionNumber second = first.next();
        assertEquals(2, second.value());
        assertTrue(second.isAfter(first));
        assertFalse(first.isAfter(second));
    }

    @Test
    void comparableOrdersByValue() {
        assertTrue(RevisionNumber.of(1).compareTo(RevisionNumber.of(2)) < 0);
        assertEquals(0, RevisionNumber.of(3).compareTo(RevisionNumber.of(3)));
    }

    @Test
    void equalsAndHashCodeBasedOnValue() {
        assertEquals(RevisionNumber.of(5), RevisionNumber.of(5));
        assertEquals(RevisionNumber.of(5).hashCode(), RevisionNumber.of(5).hashCode());
        assertFalse(RevisionNumber.of(5).equals(RevisionNumber.of(6)));
    }

    @Test
    void toStringIsPlainNumber() {
        assertEquals("4", RevisionNumber.of(4).toString());
    }
}
