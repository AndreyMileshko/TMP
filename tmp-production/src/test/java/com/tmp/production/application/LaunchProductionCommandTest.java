package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class LaunchProductionCommandTest {

    @Test
    void validCommand() {
        UUID orderId = UUID.randomUUID();
        LaunchProductionCommand cmd = new LaunchProductionCommand(orderId, "user");
        assertEquals(orderId, cmd.sourceOrderId());
        assertEquals("user", cmd.createdBy());
    }

    @Test
    void blankCreatedByRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LaunchProductionCommand(UUID.randomUUID(), " "));
    }

    @Test
    void nullOrderIdRejected() {
        assertThrows(NullPointerException.class, () -> new LaunchProductionCommand(null, "u"));
    }
}
