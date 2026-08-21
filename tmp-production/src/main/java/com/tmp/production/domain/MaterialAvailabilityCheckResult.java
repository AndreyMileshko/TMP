package com.tmp.production.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Immutable snapshot of a material availability check for one order. */
public record MaterialAvailabilityCheckResult(
        SourceOrderId sourceOrderId,
        Instant checkedAt,
        MaterialAvailabilityOverallStatus overallStatus,
        List<MaterialAvailabilityLine> lines) {

    public MaterialAvailabilityCheckResult {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        Objects.requireNonNull(checkedAt, "checkedAt");
        Objects.requireNonNull(overallStatus, "overallStatus");
        Objects.requireNonNull(lines, "lines");
        lines = List.copyOf(lines);
    }
}
