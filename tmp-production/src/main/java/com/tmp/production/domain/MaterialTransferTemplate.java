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
 * Production-owned editable Material Transfer Template (Production Spec §13, ADR-035).
 *
 * <p>Not a Document Engine business document and not a Warehouse Transfer. Confirmation creates
 * Warehouse-owned Transfer DRAFTs via STAGE7-010 orchestration.
 */
public final class MaterialTransferTemplate {

    private final MaterialTransferTemplateId templateId;
    private final SourceOrderId sourceOrderId;
    private final UUID sourceWarehouseId;
    private final UUID destinationWarehouseId;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final List<MaterialTransferTemplateLine> lines;
    private final long version;
    private final MaterialTransferTemplateStatus status;
    private final Instant confirmedAt;

    private MaterialTransferTemplate(
            MaterialTransferTemplateId templateId,
            SourceOrderId sourceOrderId,
            UUID sourceWarehouseId,
            UUID destinationWarehouseId,
            Instant createdAt,
            Instant updatedAt,
            List<MaterialTransferTemplateLine> lines,
            long version,
            MaterialTransferTemplateStatus status,
            Instant confirmedAt) {
        this.templateId = Objects.requireNonNull(templateId, "templateId");
        this.sourceOrderId = Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        this.sourceWarehouseId = Objects.requireNonNull(sourceWarehouseId, "sourceWarehouseId");
        this.destinationWarehouseId =
                Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        this.version = version;
        this.status = Objects.requireNonNull(status, "status");
        this.confirmedAt = confirmedAt;
        if (sourceWarehouseId.equals(destinationWarehouseId)) {
            throw new IllegalArgumentException(
                    "sourceWarehouseId and destinationWarehouseId must be distinct");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must be >= 0");
        }
        if (status == MaterialTransferTemplateStatus.CONFIRMED) {
            Objects.requireNonNull(confirmedAt, "confirmedAt required when CONFIRMED");
        } else if (confirmedAt != null) {
            throw new IllegalArgumentException("confirmedAt must be null unless CONFIRMED");
        }
        validateLines(this.lines);
    }

    public static MaterialTransferTemplate create(
            SourceOrderId sourceOrderId,
            UUID sourceWarehouseId,
            UUID destinationWarehouseId,
            Instant createdAt,
            List<MaterialTransferTemplateLine> lines) {
        return new MaterialTransferTemplate(
                MaterialTransferTemplateId.generate(),
                sourceOrderId,
                sourceWarehouseId,
                destinationWarehouseId,
                createdAt,
                createdAt,
                lines,
                0L,
                MaterialTransferTemplateStatus.DRAFT,
                null);
    }

    public static MaterialTransferTemplate rehydrate(
            MaterialTransferTemplateId templateId,
            SourceOrderId sourceOrderId,
            UUID sourceWarehouseId,
            UUID destinationWarehouseId,
            Instant createdAt,
            Instant updatedAt,
            List<MaterialTransferTemplateLine> lines,
            long version,
            MaterialTransferTemplateStatus status,
            Instant confirmedAt) {
        return new MaterialTransferTemplate(
                templateId,
                sourceOrderId,
                sourceWarehouseId,
                destinationWarehouseId,
                createdAt,
                updatedAt,
                lines,
                version,
                status,
                confirmedAt);
    }

    public MaterialTransferTemplate changeRequestedQuantity(
            MaterialTransferTemplateLineId lineId, BigDecimal quantity, Instant updatedAt) {
        ensureEditable();
        Objects.requireNonNull(lineId, "lineId");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(updatedAt, "updatedAt");
        return replaceLine(lineId, line -> line.changeRequestedQuantity(quantity), updatedAt);
    }

    public MaterialTransferTemplate excludeLine(
            MaterialTransferTemplateLineId lineId, Instant updatedAt) {
        ensureEditable();
        Objects.requireNonNull(lineId, "lineId");
        Objects.requireNonNull(updatedAt, "updatedAt");
        return replaceLine(lineId, MaterialTransferTemplateLine::exclude, updatedAt);
    }

    public MaterialTransferTemplate restoreLine(
            MaterialTransferTemplateLineId lineId, Instant updatedAt) {
        ensureEditable();
        Objects.requireNonNull(lineId, "lineId");
        Objects.requireNonNull(updatedAt, "updatedAt");
        return replaceLine(lineId, MaterialTransferTemplateLine::restore, updatedAt);
    }

    /**
     * Marks this template confirmed after Warehouse Transfer DRAFTs were created successfully.
     */
    public MaterialTransferTemplate confirm(Instant confirmedAt) {
        Objects.requireNonNull(confirmedAt, "confirmedAt");
        if (status == MaterialTransferTemplateStatus.CONFIRMED) {
            return this;
        }
        if (status != MaterialTransferTemplateStatus.DRAFT) {
            throw new IllegalStateException("Cannot confirm template in status " + status);
        }
        if (includedTransferLines().isEmpty()) {
            throw new IllegalStateException(
                    "Cannot confirm template without included transfer lines");
        }
        return new MaterialTransferTemplate(
                templateId,
                sourceOrderId,
                sourceWarehouseId,
                destinationWarehouseId,
                createdAt,
                confirmedAt,
                lines,
                version,
                MaterialTransferTemplateStatus.CONFIRMED,
                confirmedAt);
    }

    public void ensureEditable() {
        if (status != MaterialTransferTemplateStatus.DRAFT) {
            throw new MaterialTransferTemplateNotEditableException(templateId, status);
        }
    }

    public MaterialTransferTemplateId templateId() {
        return templateId;
    }

    public SourceOrderId sourceOrderId() {
        return sourceOrderId;
    }

    public UUID sourceWarehouseId() {
        return sourceWarehouseId;
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

    public List<MaterialTransferTemplateLine> lines() {
        return lines;
    }

    public long version() {
        return version;
    }

    public MaterialTransferTemplateStatus status() {
        return status;
    }

    public Optional<Instant> confirmedAt() {
        return Optional.ofNullable(confirmedAt);
    }

    /**
     * Lines that STAGE7-010 should turn into Warehouse Transfer operations: included and
     * requestedQuantity &gt; 0.
     */
    public List<MaterialTransferTemplateLine> includedTransferLines() {
        return lines.stream().filter(MaterialTransferTemplateLine::included).toList();
    }

    private MaterialTransferTemplate replaceLine(
            MaterialTransferTemplateLineId lineId,
            java.util.function.UnaryOperator<MaterialTransferTemplateLine> transform,
            Instant newUpdatedAt) {
        List<MaterialTransferTemplateLine> next = new ArrayList<>(lines.size());
        boolean found = false;
        for (MaterialTransferTemplateLine line : lines) {
            if (line.lineId().equals(lineId)) {
                next.add(transform.apply(line));
                found = true;
            } else {
                next.add(line);
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Unknown template lineId: " + lineId);
        }
        return new MaterialTransferTemplate(
                templateId,
                sourceOrderId,
                sourceWarehouseId,
                destinationWarehouseId,
                createdAt,
                newUpdatedAt,
                next,
                version,
                status,
                confirmedAt);
    }

    private static void validateLines(List<MaterialTransferTemplateLine> lines) {
        Set<MaterialTransferTemplateLineId> lineIds = new HashSet<>();
        Set<MaterialReferenceId> activeMaterials = new HashSet<>();
        for (MaterialTransferTemplateLine line : lines) {
            Objects.requireNonNull(line, "line");
            if (!lineIds.add(line.lineId())) {
                throw new IllegalArgumentException("Duplicate template lineId: " + line.lineId());
            }
            if (line.included() && !activeMaterials.add(line.materialReferenceId())) {
                throw new IllegalArgumentException(
                        "Duplicate active transfer line for materialReferenceId: "
                                + line.materialReferenceId());
            }
        }
    }
}
