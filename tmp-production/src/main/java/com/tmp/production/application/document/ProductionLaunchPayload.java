package com.tmp.production.application.document;

import com.tmp.production.domain.SourceOrderId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable payload carried by a whole-order Production Launch document (Production Spec §9).
 *
 * <p>Contains one line per ACTIVE order item with frozen specification reference and ordered
 * quantity. No material availability, warehouse, cutting, or reservation data.
 */
public record ProductionLaunchPayload(
        SourceOrderId sourceOrderId,
        List<ProductionLaunchLine> lines,
        String createdBy,
        Instant launchTimestamp) {

    public ProductionLaunchPayload {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(launchTimestamp, "launchTimestamp");
        if (createdBy.isBlank()) {
            throw new IllegalArgumentException("createdBy must not be blank");
        }
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Production Launch payload requires at least one line");
        }
        lines = List.copyOf(lines);
    }
}
