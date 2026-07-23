package com.tmp.security.infrastructure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.domain.PasswordHash;
import org.junit.jupiter.api.Test;

class BCryptPasswordHasherTest {

    private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

    @Test
    void hashDiffersFromPlaintextAndMatches() {
        PasswordHash hash = hasher.hash("secret-pass".toCharArray());
        assertFalse(hash.encodedValue().contains("secret-pass"));
        assertNotEquals("secret-pass", hash.encodedValue());
        assertTrue(hasher.matches("secret-pass".toCharArray(), hash));
        assertFalse(hasher.matches("wrong".toCharArray(), hash));
    }

    @Test
    void samePasswordProducesDifferentHashes() {
        PasswordHash a = hasher.hash("same".toCharArray());
        PasswordHash b = hasher.hash("same".toCharArray());
        assertNotEquals(a.encodedValue(), b.encodedValue());
        assertTrue(hasher.matches("same".toCharArray(), a));
        assertTrue(hasher.matches("same".toCharArray(), b));
    }

    @Test
    void rejectsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> hasher.hash(new char[0]));
    }
}
