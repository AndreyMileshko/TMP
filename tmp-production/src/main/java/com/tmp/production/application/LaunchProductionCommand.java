package com.tmp.production.application;

import java.util.Objects;
import java.util.UUID;

/**
 * Application-level command to accept a whole ACTIVE customer order into production.
 *
 * <p>Order items, ordered quantities and specification references are resolved exclusively from
 * Order Management Public Query at Launch. Callers must not supply item-level production data.
 */
public record LaunchProductionCommand(UUID sourceOrderId, String createdBy) {

    public LaunchProductionCommand {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        Objects.requireNonNull(createdBy, "createdBy");
        if (createdBy.isBlank()) {
            throw new IllegalArgumentException("createdBy must not be blank");
        }
    }
}
