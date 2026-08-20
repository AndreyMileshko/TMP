package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class LaunchProductionCommandTest {

    @Test
    void validCommand() {
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        LaunchProductionCommand cmd = new LaunchProductionCommand(orderId, itemId, 10, "user");
        assertEquals(orderId, cmd.sourceOrderId());
        assertEquals(itemId, cmd.sourceOrderItemId());
        assertEquals(10, cmd.orderedQuantity());
    }

    @Test
    void zeroQuantityRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LaunchProductionCommand(UUID.randomUUID(), UUID.randomUUID(), 0, "u"));
    }

    @Test
    void blankCreatedByRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LaunchProductionCommand(UUID.randomUUID(), UUID.randomUUID(), 1, " "));
    }

    @Test
    void nullOrderIdRejected() {
        assertThrows(
                NullPointerException.class,
                () -> new LaunchProductionCommand(null, UUID.randomUUID(), 1, "u"));
    }
}
