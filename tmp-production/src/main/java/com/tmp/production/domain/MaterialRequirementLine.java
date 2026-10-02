package com.tmp.production.domain;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One editable line of a Production-owned Material Requirement.
 *
 * <p>Holds a single master-editable {@code quantity} (no recommended/requested dual model). Source
 * contributions preserve per-item material provenance; manual quantity edits do not change product
 * coverage on source items.
 */
public final class MaterialRequirementLine {

    private final MaterialRequirementLineId lineId;
    private final MaterialReferenceId materialReferenceId;
    private final String materialCode;
    private final String materialName;
    private final String color;
    private final String unitOfMeasure;
    private final BigDecimal quantity;
    private final List<MaterialRequirementLineContribution> contributions;

    private MaterialRequirementLine(
            MaterialRequirementLineId lineId,
            MaterialReferenceId materialReferenceId,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            BigDecimal quantity,
            List<MaterialRequirementLineContribution> contributions) {
        this.lineId = Objects.requireNonNull(lineId, "lineId");
        this.materialReferenceId =
                Objects.requireNonNull(materialReferenceId, "materialReferenceId");
        this.materialCode = Objects.requireNonNull(materialCode, "materialCode");
        this.materialName = materialName;
        this.color = SpecificationMaterialIdentity.normalizeColor(color);
        this.unitOfMeasure = Objects.requireNonNull(unitOfMeasure, "unitOfMeasure").trim();
        this.quantity = requirePositive(quantity, "quantity");
        this.contributions =
                List.copyOf(Objects.requireNonNull(contributions, "contributions"));
        if (this.contributions.isEmpty()) {
            throw new IllegalArgumentException("contributions must not be empty");
        }
        validateContributionKeys(this.contributions);
    }

    public static MaterialRequirementLine create(
            MaterialReferenceId materialReferenceId,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            BigDecimal quantity,
            List<MaterialRequirementLineContribution> contributions) {
        return new MaterialRequirementLine(
                MaterialRequirementLineId.generate(),
                materialReferenceId,
                materialCode,
                materialName,
                color,
                unitOfMeasure,
                quantity,
                contributions);
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
            List<MaterialRequirementLineContribution> contributions) {
        return new MaterialRequirementLine(
                lineId,
                materialReferenceId,
                materialCode,
                materialName,
                color,
                unitOfMeasure,
                quantity,
                contributions);
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
                contributions);
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

    public List<MaterialRequirementLineContribution> contributions() {
        return contributions;
    }

    /** Distinct source Order Item ids contributing to this material line. */
    public Set<SourceOrderItemId> sourceOrderItemIds() {
        Set<SourceOrderItemId> ids = new LinkedHashSet<>();
        for (MaterialRequirementLineContribution contribution : contributions) {
            ids.add(contribution.sourceOrderItemId());
        }
        return Set.copyOf(ids);
    }

    private static void validateContributionKeys(
            List<MaterialRequirementLineContribution> contributions) {
        Set<MaterialRequirementSourceItemKey> keys = new LinkedHashSet<>();
        for (MaterialRequirementLineContribution contribution : contributions) {
            Objects.requireNonNull(contribution, "contribution");
            MaterialRequirementSourceItemKey key =
                    MaterialRequirementSourceItemKey.of(
                            contribution.sourceOrderId(), contribution.sourceOrderItemId());
            if (!keys.add(key)) {
                throw new IllegalArgumentException(
                        "Duplicate line contribution for source item: " + key);
            }
        }
    }

    private static BigDecimal requirePositive(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be > 0: " + value);
        }
        return value;
    }
}
