package com.tmp.capability.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CapabilityIdTest {

    @Test
    void validIdConstructsSuccessfully() {
        CapabilityId id = CapabilityId.of("sample.technical.capability");

        assertEquals("sample.technical.capability", id.value());
        assertEquals("sample.technical.capability", id.toString());
    }

    @Test
    void blankIdRejected() {
        assertThrows(IllegalArgumentException.class, () -> CapabilityId.of("   "));
    }

    @Test
    void emptyIdRejected() {
        assertThrows(IllegalArgumentException.class, () -> CapabilityId.of(""));
    }

    @Test
    void nullIdRejected() {
        assertThrows(NullPointerException.class, () -> CapabilityId.of(null));
    }

    @Test
    void equalsAndHashCodeByValue() {
        CapabilityId first = CapabilityId.of("sample.capability");
        CapabilityId second = CapabilityId.of("sample.capability");
        CapabilityId different = CapabilityId.of("other.capability");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, different);
    }
}
