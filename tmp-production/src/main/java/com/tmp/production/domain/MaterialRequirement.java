package com.tmp.production.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Production-owned editable Material Requirement (Production Spec §13 TARGET / Stage 3.5.9).
 *
 * <p>Not a Document Engine business document and not a Warehouse Transfer. Submit / routing is
 * Stage 3.5.10.
 */
public final class MaterialRequirement {

    private final MaterialRequirementId requirementId;
    private final SourceOrderId sourceOrderId;
    private final UUID destinationWarehouseId;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final long version;
    private final MaterialRequirementStatus status;
    private final List<MaterialRequirementLine> lines;

    private MaterialRequirement(
            MaterialRequirementId requirementId,
            SourceOrderId sourceOrderId,
            UUID destinationWarehouseId,
            Instant createdAt,
            Instant updatedAt,
            long version,
            MaterialRequirementStatus status,
            List<MaterialRequirementLine> lines) {
        this.requirementId = Objects.requireNonNull(requirementId, "requirementId");
        this.sourceOrderId = Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        this.destinationWarehouseId =
                Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
        this.status = Objects.requireNonNull(status, "status");
        this.lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        if (version < 0) {
            throw new IllegalArgumentException("version must be >= 0");
        }
        validateLines(this.lines);
    }

    public static MaterialRequirement create(
            SourceOrderId sourceOrderId,
            UUID destinationWarehouseId,
            Instant createdAt,
            List<MaterialRequirementLine> lines) {
        return new MaterialRequirement(
                MaterialRequirementId.generate(),
                sourceOrderId,
                destinationWarehouseId,
                createdAt,
                createdAt,
                0L,
                MaterialRequirementStatus.DRAFT,
                lines);
    }

    public static MaterialRequirement rehydrate(
            MaterialRequirementId requirementId,
            SourceOrderId sourceOrderId,
            UUID destinationWarehouseId,
            Instant createdAt,
            Instant updatedAt,
            long version,
            MaterialRequirementStatus status,
            List<MaterialRequirementLine> lines) {
        return new MaterialRequirement(
                requirementId,
                sourceOrderId,
                destinationWarehouseId,
                createdAt,
                updatedAt,
                version,
                status,
                lines);
    }

    /**
     * Changes line quantity. Domain keeps the same optimistic-lock {@code version}; the repository
     * increments version on successful save (same pattern as {@link MaterialTransferTemplate}).
     */
    public MaterialRequirement changeLineQuantity(
            MaterialRequirementLineId lineId, BigDecimal quantity, Instant updatedAt) {
        ensureEditable();
        Objects.requireNonNull(lineId, "lineId");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(updatedAt, "updatedAt");
        List<MaterialRequirementLine> next = new ArrayList<>(lines.size());
        boolean found = false;
        for (MaterialRequirementLine line : lines) {
            if (line.lineId().equals(lineId)) {
                next.add(line.changeQuantity(quantity));
                found = true;
            } else {
                next.add(line);
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Unknown requirement lineId: " + lineId);
        }
        return new MaterialRequirement(
                requirementId,
                sourceOrderId,
                destinationWarehouseId,
                createdAt,
                updatedAt,
                version,
                status,
                next);
    }

    public void ensureEditable() {
        if (status != MaterialRequirementStatus.DRAFT) {
            throw new IllegalStateException(
                    "Material requirement is not editable in status " + status);
        }
    }

    public MaterialRequirementId requirementId() {
        return requirementId;
    }

    public SourceOrderId sourceOrderId() {
        return sourceOrderId;
    }

    public UUID destinationWarehouseId() {
        return destinationWarehouseId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }

    public MaterialRequirementStatus status() {
        return status;
    }

    public List<MaterialRequirementLine> lines() {
        return lines;
    }

    private static void validateLines(List<MaterialRequirementLine> lines) {
        Set<MaterialRequirementLineId> lineIds = new HashSet<>();
        Set<MaterialReferenceId> materials = new HashSet<>();
        for (MaterialRequirementLine line : lines) {
            Objects.requireNonNull(line, "line");
            if (!lineIds.add(line.lineId())) {
                throw new IllegalArgumentException(
                        "Duplicate requirement lineId: " + line.lineId());
            }
            if (!materials.add(line.materialReferenceId())) {
                throw new IllegalArgumentException(
                        "Duplicate requirement line for materialReferenceId: "
                                + line.materialReferenceId());
            }
        }
    }
}
