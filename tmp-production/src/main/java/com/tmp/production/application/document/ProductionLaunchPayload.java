package com.tmp.production.application.document;

import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable payload carried by a Production Launch document (Production Spec §9).
 *
 * <p>Contains only the data required at launch. No material availability, warehouse,
 * cutting, or reservation data.
 */
public record ProductionLaunchPayload(
        SourceOrderId sourceOrderId,
        SourceOrderItemId sourceOrderItemId,
        SpecificationId specificationId,
        ProductionQuantity orderedQuantity,
        Instant launchTimestamp,
        String createdBy) {

    public ProductionLaunchPayload {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
        Objects.requireNonNull(specificationId, "specificationId");
        Objects.requireNonNull(orderedQuantity, "orderedQuantity");
        Objects.requireNonNull(launchTimestamp, "launchTimestamp");
        Objects.requireNonNull(createdBy, "createdBy");
        if (createdBy.isBlank()) {
            throw new IllegalArgumentException("createdBy must not be blank");
        }
    }
}
