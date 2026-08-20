package com.tmp.order.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpecificationIdTest {

    @Test
    void equality() {
        UUID raw = UUID.randomUUID();
        assertEquals(SpecificationId.of(raw), SpecificationId.of(raw));
    }

    @Test
    void inequality() {
        assertNotEquals(SpecificationId.of(UUID.randomUUID()), SpecificationId.of(UUID.randomUUID()));
    }

    @Test
    void nullRejected() {
        assertThrows(NullPointerException.class, () -> SpecificationId.of(null));
    }

    @Test
    void toStringReturnsUuid() {
        UUID raw = UUID.randomUUID();
        assertEquals(raw.toString(), SpecificationId.of(raw).toString());
    }
}
