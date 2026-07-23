package com.tmp.security.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionIdTest {

    @Test
    void generateAndOfRoundTrip() {
        SessionId generated = SessionId.generate();
        assertNotNull(generated.value());
        SessionId restored = SessionId.of(generated.value());
        assertEquals(generated, restored);
        assertEquals(generated.hashCode(), restored.hashCode());
        assertEquals(generated.value().toString(), generated.toString());
    }

    @Test
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> SessionId.of(null));
    }

    @Test
    void differentValuesNotEqual() {
        assertNotEquals(SessionId.of(UUID.randomUUID()), SessionId.of(UUID.randomUUID()));
    }
}
