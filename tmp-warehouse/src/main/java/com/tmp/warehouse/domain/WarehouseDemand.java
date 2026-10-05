package com.tmp.warehouse.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Warehouse-owned Demand aggregate: operational warehouse need accepted from Production.
 *
 * <p>Distinct from Production Material Requirement and from Transfer. Status / received quantity
 * are not persisted — future views derive them. Whole-Demand cancellation metadata may be stored
 * on the header only.
 */
public final class WarehouseDemand {

    private final WarehouseDemandId id;
    private final UUID sourceMaterialRequirementId;
    private final WarehouseId destinationWarehouseId;
    private final Instant acceptedAt;
    private final String acceptedBy;
    private final Instant cancelledAt;
    private final String cancelledBy;
    private final long version;
    private final List<WarehouseDemandLine> lines;

    private WarehouseDemand(
            WarehouseDemandId id,
            UUID sourceMaterialRequirementId,
            WarehouseId destinationWarehouseId,
            Instant acceptedAt,
            String acceptedBy,
            Instant cancelledAt,
            String cancelledBy,
            long version,
            List<WarehouseDemandLine> lines) {
        this.id = id;
        this.sourceMaterialRequirementId = sourceMaterialRequirementId;
        this.destinationWarehouseId = destinationWarehouseId;
        this.acceptedAt = acceptedAt;
        this.acceptedBy = acceptedBy;
        this.cancelledAt = cancelledAt;
        this.cancelledBy = cancelledBy;
        this.version = version;
        this.lines = List.copyOf(lines);
    }

    public static WarehouseDemand accept(
            UUID sourceMaterialRequirementId,
            WarehouseId destinationWarehouseId,
            Instant acceptedAt,
            String acceptedBy,
            List<WarehouseDemandLine> lines) {
        return of(
                WarehouseDemandId.generate(),
                sourceMaterialRequirementId,
                destinationWarehouseId,
                acceptedAt,
                acceptedBy,
                null,
                null,
                0L,
                lines);
    }

    public static WarehouseDemand of(
            WarehouseDemandId id,
            UUID sourceMaterialRequirementId,
            WarehouseId destinationWarehouseId,
            Instant acceptedAt,
            String acceptedBy,
            Instant cancelledAt,
            String cancelledBy,
            long version,
            List<WarehouseDemandLine> lines) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourceMaterialRequirementId, "sourceMaterialRequirementId");
        Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
        Objects.requireNonNull(acceptedAt, "acceptedAt");
        Objects.requireNonNull(lines, "lines");
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Demand must have at least one line");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must be >= 0: " + version);
        }
        if (cancelledAt == null && cancelledBy != null) {
            throw new IllegalArgumentException("cancelledBy requires cancelledAt");
        }
        String normalizedAcceptedBy = normalizeActor(acceptedBy);
        String normalizedCancelledBy = normalizeActor(cancelledBy);
        validateDistinctSourceLines(lines);
        return new WarehouseDemand(
                id,
                sourceMaterialRequirementId,
                destinationWarehouseId,
                acceptedAt,
                normalizedAcceptedBy,
                cancelledAt,
                normalizedCancelledBy,
                version,
                lines);
    }

    public WarehouseDemandId id() {
        return id;
    }

    public UUID sourceMaterialRequirementId() {
        return sourceMaterialRequirementId;
    }

    public WarehouseId destinationWarehouseId() {
        return destinationWarehouseId;
    }

    public Instant acceptedAt() {
        return acceptedAt;
    }

    public Optional<String> acceptedBy() {
        return Optional.ofNullable(acceptedBy);
    }

    public Optional<Instant> cancelledAt() {
        return Optional.ofNullable(cancelledAt);
    }

    public Optional<String> cancelledBy() {
        return Optional.ofNullable(cancelledBy);
    }

    public long version() {
        return version;
    }

    public List<WarehouseDemandLine> lines() {
        return lines;
    }

    public boolean isCancelled() {
        return cancelledAt != null;
    }

    private static void validateDistinctSourceLines(List<WarehouseDemandLine> lines) {
        Set<UUID> sourceLineIds = new HashSet<>();
        List<WarehouseDemandLine> ordered = new ArrayList<>(lines);
        for (WarehouseDemandLine line : ordered) {
            Objects.requireNonNull(line, "line");
            if (!sourceLineIds.add(line.sourceMaterialRequirementLineId())) {
                throw new IllegalArgumentException(
                        "Duplicate sourceMaterialRequirementLineId: "
                                + line.sourceMaterialRequirementLineId());
            }
        }
    }

    private static String normalizeActor(String actor) {
        if (actor == null) {
            return null;
        }
        String trimmed = actor.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WarehouseDemand that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
