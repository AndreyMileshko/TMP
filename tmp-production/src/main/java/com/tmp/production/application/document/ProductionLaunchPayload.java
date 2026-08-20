package com.tmp.production.application.document;

import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionQuantity;
import java.util.Objects;

/**
 * Immutable payload carried by a Production Launch document (Production Spec §9).
 *
 * <p>Contains the frozen {@link ProductionFoundation} and launch quantities. No material
 * availability, warehouse, cutting, or reservation data.
 */
public record ProductionLaunchPayload(
        ProductionFoundation foundation,
        ProductionQuantity orderedQuantity,
        String createdBy) {

    public ProductionLaunchPayload {
        Objects.requireNonNull(foundation, "foundation");
        Objects.requireNonNull(orderedQuantity, "orderedQuantity");
        Objects.requireNonNull(createdBy, "createdBy");
        if (createdBy.isBlank()) {
            throw new IllegalArgumentException("createdBy must not be blank");
        }
    }
}
