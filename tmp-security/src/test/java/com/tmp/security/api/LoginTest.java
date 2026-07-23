package com.tmp.security.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LoginTest {

    @Test
    void acceptsValidAndPreservesCase() {
        Login login = Login.of("  AdminUser  ");
        assertEquals("AdminUser", login.value());
        assertEquals("AdminUser", login.toString());
    }

    @Test
    void rejectsBlankAndNull() {
        assertThrows(IllegalArgumentException.class, () -> Login.of("   "));
        assertThrows(NullPointerException.class, () -> Login.of(null));
    }

    @Test
    void rejectsOverLength() {
        String tooLong = "a".repeat(129);
        assertThrows(IllegalArgumentException.class, () -> Login.of(tooLong));
    }

    @Test
    void equalsAndHashCode() {
        Login a = Login.of("admin");
        Login b = Login.of("admin");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
