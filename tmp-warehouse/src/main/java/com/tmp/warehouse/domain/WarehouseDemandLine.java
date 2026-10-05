package com.tmp.warehouse.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One immutable-snapshot line of a Warehouse-owned Demand.
 *
 * <p>Snapshot fields (source line id, material identity, UoM, length, required quantity) are fixed
 * at acceptance. {@code materialReferenceId} and {@code waitingReason} are optional operational
 * attributes and may be null.
 */
public final class WarehouseDemandLine {

    private final WarehouseDemandLineId id;
    private final UUID sourceMaterialRequirementLineId;
    private final String materialCode;
    private final String materialName;
    private final String color;
    private final String unitOfMeasure;
    private final BigDecimal lengthMm;
    private final StockQuantity requiredQuantity;
    private final MaterialReferenceId materialReferenceId;
    private final WarehouseDemandWaitingReason waitingReason;

    private WarehouseDemandLine(
            WarehouseDemandLineId id,
            UUID sourceMaterialRequirementLineId,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            BigDecimal lengthMm,
            StockQuantity requiredQuantity,
            MaterialReferenceId materialReferenceId,
            WarehouseDemandWaitingReason waitingReason) {
        this.id = id;
        this.sourceMaterialRequirementLineId = sourceMaterialRequirementLineId;
        this.materialCode = materialCode;
        this.materialName = materialName;
        this.color = color;
        this.unitOfMeasure = unitOfMeasure;
        this.lengthMm = lengthMm;
        this.requiredQuantity = requiredQuantity;
        this.materialReferenceId = materialReferenceId;
        this.waitingReason = waitingReason;
    }

    public static WarehouseDemandLine create(
            UUID sourceMaterialRequirementLineId,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            BigDecimal lengthMm,
            StockQuantity requiredQuantity,
            MaterialReferenceId materialReferenceId,
            WarehouseDemandWaitingReason waitingReason) {
        return of(
                WarehouseDemandLineId.generate(),
                sourceMaterialRequirementLineId,
                materialCode,
                materialName,
                color,
                unitOfMeasure,
                lengthMm,
                requiredQuantity,
                materialReferenceId,
                waitingReason);
    }

    public static WarehouseDemandLine of(
            WarehouseDemandLineId id,
            UUID sourceMaterialRequirementLineId,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            BigDecimal lengthMm,
            StockQuantity requiredQuantity,
            MaterialReferenceId materialReferenceId,
            WarehouseDemandWaitingReason waitingReason) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourceMaterialRequirementLineId, "sourceMaterialRequirementLineId");
        String code = requireNonBlank(materialCode, "materialCode");
        String unit = requireNonBlank(unitOfMeasure, "unitOfMeasure");
        Objects.requireNonNull(requiredQuantity, "requiredQuantity");
        if (requiredQuantity.value().signum() <= 0) {
            throw new IllegalArgumentException(
                    "requiredQuantity must be > 0: " + requiredQuantity.value());
        }
        if (lengthMm != null && lengthMm.signum() <= 0) {
            throw new IllegalArgumentException("lengthMm must be > 0 when present: " + lengthMm);
        }
        String normalizedName = normalizeNullableName(materialName);
        String normalizedColor = color == null ? "" : color.trim();
        return new WarehouseDemandLine(
                id,
                sourceMaterialRequirementLineId,
                code,
                normalizedName,
                normalizedColor,
                unit.trim(),
                lengthMm,
                requiredQuantity,
                materialReferenceId,
                waitingReason);
    }

    public WarehouseDemandLineId id() {
        return id;
    }

    public UUID sourceMaterialRequirementLineId() {
        return sourceMaterialRequirementLineId;
    }

    public String materialCode() {
        return materialCode;
    }

    /** May be null when Production/specification snapshot had no name. */
    public String materialName() {
        return materialName;
    }

    public String color() {
        return color;
    }

    public String unitOfMeasure() {
        return unitOfMeasure;
    }

    public Optional<BigDecimal> lengthMm() {
        return Optional.ofNullable(lengthMm);
    }

    public StockQuantity requiredQuantity() {
        return requiredQuantity;
    }

    public Optional<MaterialReferenceId> materialReferenceId() {
        return Optional.ofNullable(materialReferenceId);
    }

    public Optional<WarehouseDemandWaitingReason> waitingReason() {
        return Optional.ofNullable(waitingReason);
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }

    private static String normalizeNullableName(String materialName) {
        if (materialName == null) {
            return null;
        }
        String trimmed = materialName.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WarehouseDemandLine that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
