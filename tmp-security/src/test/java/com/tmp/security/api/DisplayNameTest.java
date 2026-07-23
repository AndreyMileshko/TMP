package com.tmp.security.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DisplayNameTest {

    @Test
    void acceptsValidAndTrims() {
        DisplayName name = DisplayName.of("  Alice Admin  ");
        assertEquals("Alice Admin", name.value());
        assertEquals("Alice Admin", name.toString());
    }

    @Test
    void rejectsBlankAndNull() {
        assertThrows(IllegalArgumentException.class, () -> DisplayName.of("   "));
        assertThrows(NullPointerException.class, () -> DisplayName.of(null));
    }

    @Test
    void rejectsOverLength() {
        String tooLong = "a".repeat(256);
        assertThrows(IllegalArgumentException.class, () -> DisplayName.of(tooLong));
    }

    @Test
    void equalsAndHashCode() {
        DisplayName a = DisplayName.of("Alice");
        DisplayName b = DisplayName.of("Alice");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
