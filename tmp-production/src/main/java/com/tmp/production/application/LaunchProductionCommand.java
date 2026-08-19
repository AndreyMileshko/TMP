package com.tmp.production.application;

import java.util.Objects;
import java.util.UUID;

/**
 * Application-level command to launch production for a single order item.
 *
 * <p>Carries only the identifiers required by Production Launch. Specification content
 * is not copied — only the stable {@code specificationId} from Order Management.
 */
public record LaunchProductionCommand(
        UUID sourceOrderId,
        UUID sourceOrderItemId,
        UUID specificationId,
        long orderedQuantity,
        String createdBy) {

    public LaunchProductionCommand {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
        Objects.requireNonNull(specificationId, "specificationId");
        Objects.requireNonNull(createdBy, "createdBy");
        if (orderedQuantity <= 0) {
            throw new IllegalArgumentException("orderedQuantity must be positive");
        }
        if (createdBy.isBlank()) {
            throw new IllegalArgumentException("createdBy must not be blank");
        }
    }
}
