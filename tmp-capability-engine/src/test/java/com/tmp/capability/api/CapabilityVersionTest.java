package com.tmp.capability.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CapabilityVersionTest {

    @Test
    void validVersionParsesComponents() {
        CapabilityVersion version = CapabilityVersion.of("1.2.3");

        assertEquals(1, version.major());
        assertEquals(2, version.minor());
        assertEquals(3, version.patch());
        assertEquals("1.2.3", version.toString());
    }

    @Test
    void malformedVersionMissingPartRejected() {
        assertThrows(IllegalArgumentException.class, () -> CapabilityVersion.of("1.2"));
    }

    @Test
    void malformedVersionNonNumericRejected() {
        assertThrows(IllegalArgumentException.class, () -> CapabilityVersion.of("1.a.3"));
    }

    @Test
    void malformedVersionNegativeRejected() {
        assertThrows(IllegalArgumentException.class, () -> CapabilityVersion.of("1.-2.3"));
    }

    @Test
    void malformedVersionNullRejected() {
        assertThrows(NullPointerException.class, () -> CapabilityVersion.of(null));
    }

    @Test
    void compatibleWhenSameMajorAndHigherMinor() {
        CapabilityVersion actual = CapabilityVersion.of("2.5.0");
        CapabilityVersion required = CapabilityVersion.of("2.1.0");

        assertTrue(actual.isCompatibleWith(required));
    }

    @Test
    void compatibleWhenSameMajorMinorAndEqualPatch() {
        CapabilityVersion actual = CapabilityVersion.of("2.1.3");
        CapabilityVersion required = CapabilityVersion.of("2.1.3");

        assertTrue(actual.isCompatibleWith(required));
    }

    @Test
    void compatibleWhenSameMajorMinorAndHigherPatch() {
        CapabilityVersion actual = CapabilityVersion.of("2.1.9");
        CapabilityVersion required = CapabilityVersion.of("2.1.3");

        assertTrue(actual.isCompatibleWith(required));
    }

    @Test
    void incompatibleWhenDifferentMajor() {
        CapabilityVersion actual = CapabilityVersion.of("3.0.0");
        CapabilityVersion required = CapabilityVersion.of("2.9.9");

        assertFalse(actual.isCompatibleWith(required));
    }

    @Test
    void incompatibleWhenSameMajorLowerMinor() {
        CapabilityVersion actual = CapabilityVersion.of("2.0.9");
        CapabilityVersion required = CapabilityVersion.of("2.1.0");

        assertFalse(actual.isCompatibleWith(required));
    }

    @Test
    void incompatibleWhenSameMajorMinorLowerPatch() {
        CapabilityVersion actual = CapabilityVersion.of("2.1.1");
        CapabilityVersion required = CapabilityVersion.of("2.1.3");

        assertFalse(actual.isCompatibleWith(required));
    }

    @Test
    void comparisonIsDeterministicAndTransitive() {
        CapabilityVersion low = CapabilityVersion.of("1.0.0");
        CapabilityVersion mid = CapabilityVersion.of("1.5.0");
        CapabilityVersion high = CapabilityVersion.of("2.0.0");

        assertTrue(low.compareTo(mid) < 0);
        assertTrue(mid.compareTo(high) < 0);
        assertTrue(low.compareTo(high) < 0);
        assertEquals(0, low.compareTo(CapabilityVersion.of("1.0.0")));
    }

    @Test
    void equalsAndHashCodeByNumericComponents() {
        CapabilityVersion first = CapabilityVersion.of("1.2.3");
        CapabilityVersion second = CapabilityVersion.of("1.2.3");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }
}
