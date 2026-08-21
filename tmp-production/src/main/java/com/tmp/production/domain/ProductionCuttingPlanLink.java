package com.tmp.production.domain;

import java.util.Objects;

/**
 * Immutable Production-owned link from a Warehouse Material Reference to a Cutting Plan.
 *
 * <p>Stores opaque references only — no Cutting Plan contents, revision, or lifecycle status.
 */
public final class ProductionCuttingPlanLink {

    private final MaterialReferenceId materialReferenceId;
    private final CuttingPlanId cuttingPlanId;

    private ProductionCuttingPlanLink(
            MaterialReferenceId materialReferenceId, CuttingPlanId cuttingPlanId) {
        this.materialReferenceId =
                Objects.requireNonNull(materialReferenceId, "materialReferenceId");
        this.cuttingPlanId = Objects.requireNonNull(cuttingPlanId, "cuttingPlanId");
    }

    public static ProductionCuttingPlanLink of(
            MaterialReferenceId materialReferenceId, CuttingPlanId cuttingPlanId) {
        return new ProductionCuttingPlanLink(materialReferenceId, cuttingPlanId);
    }

    public MaterialReferenceId materialReferenceId() {
        return materialReferenceId;
    }

    public CuttingPlanId cuttingPlanId() {
        return cuttingPlanId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProductionCuttingPlanLink that)) {
            return false;
        }
        return materialReferenceId.equals(that.materialReferenceId)
                && cuttingPlanId.equals(that.cuttingPlanId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(materialReferenceId, cuttingPlanId);
    }

    @Override
    public String toString() {
        return "ProductionCuttingPlanLink{"
                + "materialReferenceId="
                + materialReferenceId
                + ", cuttingPlanId="
                + cuttingPlanId
                + '}';
    }
}
