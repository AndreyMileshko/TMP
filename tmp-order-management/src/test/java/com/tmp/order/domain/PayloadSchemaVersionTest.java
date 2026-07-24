package com.tmp.order.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PayloadSchemaVersionTest {

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void ofRejectsValuesBelowOne(int invalid) {
        assertThrows(IllegalArgumentException.class, () -> PayloadSchemaVersion.of(invalid));
    }

    @Test
    void initialIsOne() {
        assertEquals(1, PayloadSchemaVersion.initial().value());
    }

    @Test
    void ofPreservesValue() {
        assertEquals(3, PayloadSchemaVersion.of(3).value());
    }

    @Test
    void equalsAndHashCodeBasedOnValue() {
        assertEquals(PayloadSchemaVersion.of(2), PayloadSchemaVersion.of(2));
        assertEquals(PayloadSchemaVersion.of(2).hashCode(), PayloadSchemaVersion.of(2).hashCode());
    }

    @Test
    void toStringIsVersionTag() {
        assertEquals("v2", PayloadSchemaVersion.of(2).toString());
    }
}
