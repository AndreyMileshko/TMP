package com.tmp.security.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserIdTest {

    @Test
    void generateAndOfRoundTrip() {
        UserId generated = UserId.generate();
        assertNotNull(generated.value());
        UserId restored = UserId.of(generated.value());
        assertEquals(generated, restored);
        assertEquals(generated.hashCode(), restored.hashCode());
        assertEquals(generated.value().toString(), generated.toString());
    }

    @Test
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> UserId.of(null));
    }

    @Test
    void differentValuesNotEqual() {
        assertNotEquals(UserId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()));
    }
}
