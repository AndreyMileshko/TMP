package com.tmp.production.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
    private final Instant submittedAt;
    private final String submittedBy;
    private final List<MaterialRequirementLine> lines;

    private MaterialRequirement(
            MaterialRequirementId requirementId,
            SourceOrderId sourceOrderId,
            UUID destinationWarehouseId,
            Instant createdAt,
            Instant updatedAt,
            long version,
            MaterialRequirementStatus status,
            Instant submittedAt,
            String submittedBy,
            List<MaterialRequirementLine> lines) {
        this.requirementId = Objects.requireNonNull(requirementId, "requirementId");
        this.sourceOrderId = Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        this.destinationWarehouseId =
                Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
        this.status = Objects.requireNonNull(status, "status");
        this.submittedAt = submittedAt;
        this.submittedBy = submittedBy;
        this.lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        if (version < 0) {
            throw new IllegalArgumentException("version must be >= 0");
        }
        validateSubmissionMetadata(status, submittedAt, submittedBy);
        validateLines(this.lines);
    }

    private static void validateSubmissionMetadata(
            MaterialRequirementStatus status, Instant submittedAt, String submittedBy) {
        switch (status) {
            case DRAFT -> {
                if (submittedAt != null || submittedBy != null) {
                    throw new IllegalArgumentException(
                            "DRAFT material requirement must not carry submission metadata");
                }
            }
            case SUBMITTED -> {
                if (submittedAt == null || submittedBy == null || submittedBy.isBlank()) {
                    throw new IllegalArgumentException(
                            "SUBMITTED material requirement requires submittedAt and submittedBy");
                }
            }
        }
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
                null,
                null,
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
            Instant submittedAt,
            String submittedBy,
            List<MaterialRequirementLine> lines) {
        return new MaterialRequirement(
                requirementId,
                sourceOrderId,
                destinationWarehouseId,
                createdAt,
                updatedAt,
                version,
                status,
                submittedAt,
                submittedBy,
                lines);
    }

    /**
     * Transitions {@code DRAFT} → {@code SUBMITTED}, freezing lines and destination and setting
     * submission metadata. The optimistic {@code version} is kept unchanged here; the repository
     * increments it on the persisted submit (same pattern as {@link #changeLineQuantity}).
     */
    public MaterialRequirement submit(String submittedBy, Instant submittedAt) {
        Objects.requireNonNull(submittedBy, "submittedBy");
        Objects.requireNonNull(submittedAt, "submittedAt");
        if (submittedBy.isBlank()) {
            throw new IllegalArgumentException("submittedBy must not be blank");
        }
        if (status != MaterialRequirementStatus.DRAFT) {
            throw new IllegalStateException(
                    "Material requirement can be submitted only from DRAFT, was " + status);
        }
        if (lines.isEmpty()) {
            throw new IllegalStateException(
                    "Material requirement with no lines cannot be submitted: " + requirementId);
        }
        return new MaterialRequirement(
                requirementId,
                sourceOrderId,
                destinationWarehouseId,
                createdAt,
                submittedAt,
                version,
                MaterialRequirementStatus.SUBMITTED,
                submittedAt,
                submittedBy,
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
                submittedAt,
                submittedBy,
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

    public Optional<Instant> submittedAt() {
        return Optional.ofNullable(submittedAt);
    }

    public Optional<String> submittedBy() {
        return Optional.ofNullable(submittedBy);
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
