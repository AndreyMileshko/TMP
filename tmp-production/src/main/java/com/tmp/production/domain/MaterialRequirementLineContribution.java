package com.tmp.production.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Provenance of one aggregated material line back to a source Order Item and the material quantity
 * contributed by that item at prepare time.
 */
public final class MaterialRequirementLineContribution {

    private final SourceOrderId sourceOrderId;
    private final SourceOrderItemId sourceOrderItemId;
    private final BigDecimal contributedMaterialQuantity;

    private MaterialRequirementLineContribution(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            BigDecimal contributedMaterialQuantity) {
        this.sourceOrderId = Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        this.sourceOrderItemId = Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
        this.contributedMaterialQuantity =
                requirePositive(contributedMaterialQuantity, "contributedMaterialQuantity");
    }

    public static MaterialRequirementLineContribution of(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            BigDecimal contributedMaterialQuantity) {
        return new MaterialRequirementLineContribution(
                sourceOrderId, sourceOrderItemId, contributedMaterialQuantity);
    }

    public SourceOrderId sourceOrderId() {
        return sourceOrderId;
    }

    public SourceOrderItemId sourceOrderItemId() {
        return sourceOrderItemId;
    }

    public BigDecimal contributedMaterialQuantity() {
        return contributedMaterialQuantity;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MaterialRequirementLineContribution that)) {
            return false;
        }
        return sourceOrderId.equals(that.sourceOrderId)
                && sourceOrderItemId.equals(that.sourceOrderItemId)
                && contributedMaterialQuantity.compareTo(that.contributedMaterialQuantity) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                sourceOrderId,
                sourceOrderItemId,
                contributedMaterialQuantity.stripTrailingZeros());
    }

    private static BigDecimal requirePositive(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be > 0: " + value);
        }
        return value;
    }
}
