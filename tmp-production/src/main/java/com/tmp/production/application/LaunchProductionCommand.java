package com.tmp.production.application;

import java.util.Objects;
import java.util.UUID;

/**
 * Application-level command to launch production for a single order item.
 *
 * <p>Specification is resolved and frozen at Launch from Order Management Public Query.
 * Callers do not supply {@code specificationId}.
 */
public record LaunchProductionCommand(
        UUID sourceOrderId, UUID sourceOrderItemId, long orderedQuantity, String createdBy) {

    public LaunchProductionCommand {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
        Objects.requireNonNull(createdBy, "createdBy");
        if (orderedQuantity <= 0) {
            throw new IllegalArgumentException("orderedQuantity must be positive");
        }
        if (createdBy.isBlank()) {
            throw new IllegalArgumentException("createdBy must not be blank");
        }
    }
}
