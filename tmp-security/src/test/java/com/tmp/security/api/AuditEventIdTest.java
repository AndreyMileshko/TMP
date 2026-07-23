package com.tmp.security.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEventIdTest {

    @Test
    void generateAndOfRoundTrip() {
        AuditEventId generated = AuditEventId.generate();
        assertNotNull(generated.value());
        AuditEventId restored = AuditEventId.of(generated.value());
        assertEquals(generated, restored);
        assertEquals(generated.hashCode(), restored.hashCode());
        assertEquals(generated.value().toString(), generated.toString());
    }

    @Test
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> AuditEventId.of(null));
    }

    @Test
    void differentValuesNotEqual() {
        assertNotEquals(AuditEventId.of(UUID.randomUUID()), AuditEventId.of(UUID.randomUUID()));
    }
}
