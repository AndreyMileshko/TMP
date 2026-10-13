package com.tmp.production.domain;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One editable line of a Production-owned Material Requirement.
 *
 * <p>Holds a master-editable request {@code quantity} («К запросу»). Prepare-time
 * {@link #requiredQuantity()} from immutable contributions is «Требуется» (preliminary warehouse
 * demand). Request may be {@code 0}, below, equal to, or above required. Zero request omits the
 * Warehouse Demand line. Manual edits do not change product coverage on source items.
 *
 * <p>{@code materialReferenceId} is an optional Warehouse hint only — PREPARE does not require it.
 * {@code lengthMm} is an immutable snapshot field and is never part of material identity matching.
 */
public final class MaterialRequirementLine {

    private final MaterialRequirementLineId lineId;
    private final MaterialReferenceId materialReferenceId;
    private final String materialCode;
    private final String materialName;
    private final String color;
    private final String unitOfMeasure;
    private final BigDecimal lengthMm;
    private final BigDecimal quantity;
    private final List<MaterialRequirementLineContribution> contributions;

    private MaterialRequirementLine(
            MaterialRequirementLineId lineId,
            MaterialReferenceId materialReferenceId,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            BigDecimal lengthMm,
            BigDecimal quantity,
            List<MaterialRequirementLineContribution> contributions) {
        this.lineId = Objects.requireNonNull(lineId, "lineId");
        this.materialReferenceId = materialReferenceId;
        this.materialCode = Objects.requireNonNull(materialCode, "materialCode");
        this.materialName = materialName;
        this.color = SpecificationMaterialIdentity.normalizeColor(color);
        this.unitOfMeasure = Objects.requireNonNull(unitOfMeasure, "unitOfMeasure").trim();
        if (lengthMm != null && lengthMm.signum() <= 0) {
            throw new IllegalArgumentException("lengthMm must be > 0 when present: " + lengthMm);
        }
        this.lengthMm = lengthMm;
        this.contributions =
                List.copyOf(Objects.requireNonNull(contributions, "contributions"));
        if (this.contributions.isEmpty()) {
            throw new IllegalArgumentException("contributions must not be empty");
        }
        validateContributionKeys(this.contributions);
        this.quantity = requireRequestQuantity(quantity);
    }

    public static MaterialRequirementLine create(
            MaterialReferenceId materialReferenceId,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            BigDecimal lengthMm,
            BigDecimal quantity,
            List<MaterialRequirementLineContribution> contributions) {
        return new MaterialRequirementLine(
                MaterialRequirementLineId.generate(),
                materialReferenceId,
                materialCode,
                materialName,
                color,
                unitOfMeasure,
                lengthMm,
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
            BigDecimal lengthMm,
            BigDecimal quantity,
            List<MaterialRequirementLineContribution> contributions) {
        return new MaterialRequirementLine(
                lineId,
                materialReferenceId,
                materialCode,
                materialName,
                color,
                unitOfMeasure,
                lengthMm,
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
                lengthMm,
                quantity,
                contributions);
    }

    public MaterialRequirementLineId lineId() {
        return lineId;
    }

    /** Optional Warehouse MaterialReference hint; may be absent for unresolved materials. */
    public Optional<MaterialReferenceId> materialReferenceId() {
        return Optional.ofNullable(materialReferenceId);
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

    public Optional<BigDecimal> lengthMm() {
        return Optional.ofNullable(lengthMm);
    }

    /**
     * Request quantity («К запросу») persisted on the line. May be zero, below, equal to, or above
     * {@link #requiredQuantity()}.
     */
    public BigDecimal quantity() {
        return quantity;
    }

    /**
     * Full prepare-time material need («Требуется»): sum of immutable contribution quantities. Not
     * a separate persisted column.
     */
    public BigDecimal requiredQuantity() {
        return requiredQuantityFrom(contributions);
    }

    public List<MaterialRequirementLineContribution> contributions() {
        return contributions;
    }

    public SpecificationMaterialIdentity identity() {
        return SpecificationMaterialIdentity.of(materialCode, color, unitOfMeasure);
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

    private static BigDecimal requiredQuantityFrom(
            List<MaterialRequirementLineContribution> contributions) {
        BigDecimal sum = BigDecimal.ZERO;
        for (MaterialRequirementLineContribution contribution : contributions) {
            sum = sum.add(contribution.contributedMaterialQuantity());
        }
        return sum;
    }

    /** Request quantity: {@code quantity >= 0} (may exceed preliminary required). */
    private static BigDecimal requireRequestQuantity(BigDecimal value) {
        Objects.requireNonNull(value, "quantity");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("quantity must be >= 0: " + value);
        }
        return value;
    }
}
