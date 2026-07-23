package com.tmp.security.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoleIdTest {

    @Test
    void generateAndOfRoundTrip() {
        RoleId generated = RoleId.generate();
        assertNotNull(generated.value());
        RoleId restored = RoleId.of(generated.value());
        assertEquals(generated, restored);
        assertEquals(generated.hashCode(), restored.hashCode());
        assertEquals(generated.value().toString(), generated.toString());
    }

    @Test
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> RoleId.of(null));
    }

    @Test
    void differentValuesNotEqual() {
        assertNotEquals(RoleId.of(UUID.randomUUID()), RoleId.of(UUID.randomUUID()));
    }
}
