package com.tmp.production.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

/**
 * One editable line of a Production-owned Material Requirement.
 *
 * <p>Holds a single master-editable {@code quantity} (no recommended/requested dual model).
 */
public final class MaterialRequirementLine {

    private final MaterialRequirementLineId lineId;
    private final MaterialReferenceId materialReferenceId;
    private final String materialCode;
    private final String materialName;
    private final String color;
    private final String unitOfMeasure;
    private final BigDecimal quantity;
    private final Set<SourceOrderItemId> sourceOrderItemIds;

    private MaterialRequirementLine(
            MaterialRequirementLineId lineId,
            MaterialReferenceId materialReferenceId,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            BigDecimal quantity,
            Set<SourceOrderItemId> sourceOrderItemIds) {
        this.lineId = Objects.requireNonNull(lineId, "lineId");
        this.materialReferenceId =
                Objects.requireNonNull(materialReferenceId, "materialReferenceId");
        this.materialCode = Objects.requireNonNull(materialCode, "materialCode");
        this.materialName = materialName;
        this.color = SpecificationMaterialIdentity.normalizeColor(color);
        this.unitOfMeasure = Objects.requireNonNull(unitOfMeasure, "unitOfMeasure").trim();
        this.quantity = requirePositive(quantity, "quantity");
        this.sourceOrderItemIds =
                Set.copyOf(Objects.requireNonNull(sourceOrderItemIds, "sourceOrderItemIds"));
    }

    public static MaterialRequirementLine create(
            MaterialReferenceId materialReferenceId,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            BigDecimal quantity,
            Set<SourceOrderItemId> sourceOrderItemIds) {
        return new MaterialRequirementLine(
                MaterialRequirementLineId.generate(),
                materialReferenceId,
                materialCode,
                materialName,
                color,
                unitOfMeasure,
                quantity,
                sourceOrderItemIds);
    }

    /** Persistence / reconstruction entry point. */
    public static MaterialRequirementLine rehydrate(
            MaterialRequirementLineId lineId,
            MaterialReferenceId materialReferenceId,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            BigDecimal quantity,
            Set<SourceOrderItemId> sourceOrderItemIds) {
        return new MaterialRequirementLine(
                lineId,
                materialReferenceId,
                materialCode,
                materialName,
                color,
                unitOfMeasure,
                quantity,
                sourceOrderItemIds);
    }

    public MaterialRequirementLine changeQuantity(BigDecimal quantity) {
        return new MaterialRequirementLine(
                lineId,
                materialReferenceId,
                materialCode,
                materialName,
                color,
                unitOfMeasure,
                quantity,
                sourceOrderItemIds);
    }

    public MaterialRequirementLineId lineId() {
        return lineId;
    }

    public MaterialReferenceId materialReferenceId() {
        return materialReferenceId;
    }

    public String materialCode() {
        return materialCode;
    }

    public String materialName() {
        return materialName;
    }

    public String color() {
        return color;
    }

    public String unitOfMeasure() {
        return unitOfMeasure;
    }

    public BigDecimal quantity() {
        return quantity;
    }

    public Set<SourceOrderItemId> sourceOrderItemIds() {
        return sourceOrderItemIds;
    }

    private static BigDecimal requirePositive(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be > 0: " + value);
        }
        return value;
    }
}
