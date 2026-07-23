package com.tmp.security.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHashTest {

    @Test
    void constructionAndEquality() {
        PasswordHash a = PasswordHash.of("$2a$10$abcdefghijklmnopqrstuv");
        PasswordHash b = PasswordHash.of("$2a$10$abcdefghijklmnopqrstuv");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals("$2a$10$abcdefghijklmnopqrstuv", a.encodedValue());
    }

    @Test
    void toStringNeverContainsHash() {
        String hash1 = "$2a$10$ABCDEFGHIJKLMNOPQRSTUV";
        String hash2 = "$2a$10$ZYXWVUTSRQPONMLKJIHGFE";
        assertFalse(PasswordHash.of(hash1).toString().contains(hash1));
        assertFalse(PasswordHash.of(hash2).toString().contains(hash2));
        assertFalse(PasswordHash.of(hash1).toString().contains("ABCDEF"));
        assertTrue(PasswordHash.of(hash1).toString().contains("REDACTED"));
    }

    @Test
    void rejectsBlankAndNull() {
        assertThrows(IllegalArgumentException.class, () -> PasswordHash.of("  "));
        assertThrows(NullPointerException.class, () -> PasswordHash.of(null));
    }
}
