package com.tmp.production.domain;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One editable line of a Production-owned Material Transfer Template.
 *
 * <p>{@code recommendedQuantity} is the planning snapshot and never changes on user edit.
 * {@code requestedQuantity} is the master-editable transfer request. Exclusion uses {@code
 * included=false}; included lines require {@code requestedQuantity > 0}.
 */
public final class MaterialTransferTemplateLine {

    private final MaterialTransferTemplateLineId lineId;
    private final MaterialReferenceId materialReferenceId;
    private final String materialCode;
    private final String materialName;
    private final String color;
    private final String unitOfMeasure;
    private final BigDecimal recommendedQuantity;
    private final BigDecimal requestedQuantity;
    private final boolean included;
    private final MaterialPlanningSource planningSource;
    private final CuttingPlanId cuttingPlanId;
    private final CuttingLinkStatus cuttingLinkStatus;
    private final List<CuttingPlanId> cuttingPlanReferences;
    private final Set<SourceOrderItemId> sourceOrderItemIds;
    private final BigDecimal requiredQuantity;
    private final BigDecimal mainWarehouseAvailable;
    private final BigDecimal productionWarehouseAvailable;
    private final BigDecimal uncoveredDeficit;

    private MaterialTransferTemplateLine(
            MaterialTransferTemplateLineId lineId,
            MaterialReferenceId materialReferenceId,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            BigDecimal recommendedQuantity,
            BigDecimal requestedQuantity,
            boolean included,
            MaterialPlanningSource planningSource,
            CuttingPlanId cuttingPlanId,
            CuttingLinkStatus cuttingLinkStatus,
            List<CuttingPlanId> cuttingPlanReferences,
            Set<SourceOrderItemId> sourceOrderItemIds,
            BigDecimal requiredQuantity,
            BigDecimal mainWarehouseAvailable,
            BigDecimal productionWarehouseAvailable,
            BigDecimal uncoveredDeficit) {
        this.lineId = Objects.requireNonNull(lineId, "lineId");
        this.materialReferenceId =
                Objects.requireNonNull(materialReferenceId, "materialReferenceId");
        this.materialCode = Objects.requireNonNull(materialCode, "materialCode");
        this.materialName = materialName;
        this.color = SpecificationMaterialIdentity.normalizeColor(color);
        this.unitOfMeasure = Objects.requireNonNull(unitOfMeasure, "unitOfMeasure").trim();
        this.recommendedQuantity = requireNonNegative(recommendedQuantity, "recommendedQuantity");
        this.requestedQuantity = requireNonNegative(requestedQuantity, "requestedQuantity");
        this.included = included;
        this.planningSource = Objects.requireNonNull(planningSource, "planningSource");
        this.cuttingLinkStatus = Objects.requireNonNull(cuttingLinkStatus, "cuttingLinkStatus");
        this.cuttingPlanReferences =
                List.copyOf(Objects.requireNonNull(cuttingPlanReferences, "cuttingPlanReferences"));
        this.sourceOrderItemIds =
                Set.copyOf(Objects.requireNonNull(sourceOrderItemIds, "sourceOrderItemIds"));
        this.requiredQuantity = requireNonNegative(requiredQuantity, "requiredQuantity");
        this.mainWarehouseAvailable =
                requireNonNegative(mainWarehouseAvailable, "mainWarehouseAvailable");
        this.productionWarehouseAvailable =
                requireNonNegative(productionWarehouseAvailable, "productionWarehouseAvailable");
        this.uncoveredDeficit = requireNonNegative(uncoveredDeficit, "uncoveredDeficit");
        this.cuttingPlanId = validateCuttingPlanId(cuttingPlanId, cuttingLinkStatus);
        if (included && this.requestedQuantity.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Included transfer line requestedQuantity must be > 0");
        }
        if (this.recommendedQuantity.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Transfer template line recommendedQuantity must be > 0");
        }
        validateCuttingReferences();
    }

    public static MaterialTransferTemplateLine create(
            MaterialReferenceId materialReferenceId,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            BigDecimal recommendedQuantity,
            MaterialPlanningSource planningSource,
            CuttingPlanId cuttingPlanId,
            CuttingLinkStatus cuttingLinkStatus,
            List<CuttingPlanId> cuttingPlanReferences,
            Set<SourceOrderItemId> sourceOrderItemIds,
            BigDecimal requiredQuantity,
            BigDecimal mainWarehouseAvailable,
            BigDecimal productionWarehouseAvailable,
            BigDecimal uncoveredDeficit) {
        return new MaterialTransferTemplateLine(
                MaterialTransferTemplateLineId.generate(),
                materialReferenceId,
                materialCode,
                materialName,
                color,
                unitOfMeasure,
                recommendedQuantity,
                recommendedQuantity,
                true,
                planningSource,
                cuttingPlanId,
                cuttingLinkStatus,
                cuttingPlanReferences,
                sourceOrderItemIds,
                requiredQuantity,
                mainWarehouseAvailable,
                productionWarehouseAvailable,
                uncoveredDeficit);
    }

    /** Persistence / reconstruction entry point. */
    public static MaterialTransferTemplateLine rehydrate(
            MaterialTransferTemplateLineId lineId,
            MaterialReferenceId materialReferenceId,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            BigDecimal recommendedQuantity,
            BigDecimal requestedQuantity,
            boolean included,
            MaterialPlanningSource planningSource,
            CuttingPlanId cuttingPlanId,
            CuttingLinkStatus cuttingLinkStatus,
            List<CuttingPlanId> cuttingPlanReferences,
            Set<SourceOrderItemId> sourceOrderItemIds,
            BigDecimal requiredQuantity,
            BigDecimal mainWarehouseAvailable,
            BigDecimal productionWarehouseAvailable,
            BigDecimal uncoveredDeficit) {
        return new MaterialTransferTemplateLine(
                lineId,
                materialReferenceId,
                materialCode,
                materialName,
                color,
                unitOfMeasure,
                recommendedQuantity,
                requestedQuantity,
                included,
                planningSource,
                cuttingPlanId,
                cuttingLinkStatus,
                cuttingPlanReferences,
                sourceOrderItemIds,
                requiredQuantity,
                mainWarehouseAvailable,
                productionWarehouseAvailable,
                uncoveredDeficit);
    }

    public MaterialTransferTemplateLine changeRequestedQuantity(BigDecimal quantity) {
        Objects.requireNonNull(quantity, "quantity");
        if (!included) {
            throw new IllegalStateException(
                    "Cannot change requested quantity of an excluded line; restore it first");
        }
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException(
                    "requestedQuantity must be > 0 for included lines; use excludeLine to omit");
        }
        return copyWith(quantity, true);
    }

    public MaterialTransferTemplateLine exclude() {
        if (!included) {
            return this;
        }
        return copyWith(requestedQuantity, false);
    }

    public MaterialTransferTemplateLine restore() {
        if (included) {
            return this;
        }
        BigDecimal quantity =
                requestedQuantity.signum() > 0 ? requestedQuantity : recommendedQuantity;
        return copyWith(quantity, true);
    }

    public MaterialTransferTemplateLineId lineId() {
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

    public BigDecimal recommendedQuantity() {
        return recommendedQuantity;
    }

    public BigDecimal requestedQuantity() {
        return requestedQuantity;
    }

    public boolean included() {
        return included;
    }

    public MaterialPlanningSource planningSource() {
        return planningSource;
    }

    public Optional<CuttingPlanId> cuttingPlanId() {
        return Optional.ofNullable(cuttingPlanId);
    }

    public CuttingLinkStatus cuttingLinkStatus() {
        return cuttingLinkStatus;
    }

    public List<CuttingPlanId> cuttingPlanReferences() {
        return cuttingPlanReferences;
    }

    public Set<SourceOrderItemId> sourceOrderItemIds() {
        return sourceOrderItemIds;
    }

    public BigDecimal requiredQuantity() {
        return requiredQuantity;
    }

    public BigDecimal mainWarehouseAvailable() {
        return mainWarehouseAvailable;
    }

    public BigDecimal productionWarehouseAvailable() {
        return productionWarehouseAvailable;
    }

    public BigDecimal uncoveredDeficit() {
        return uncoveredDeficit;
    }

    private MaterialTransferTemplateLine copyWith(BigDecimal newRequested, boolean newIncluded) {
        return new MaterialTransferTemplateLine(
                lineId,
                materialReferenceId,
                materialCode,
                materialName,
                color,
                unitOfMeasure,
                recommendedQuantity,
                newRequested,
                newIncluded,
                planningSource,
                cuttingPlanId,
                cuttingLinkStatus,
                cuttingPlanReferences,
                sourceOrderItemIds,
                requiredQuantity,
                mainWarehouseAvailable,
                productionWarehouseAvailable,
                uncoveredDeficit);
    }

    private static CuttingPlanId validateCuttingPlanId(
            CuttingPlanId cuttingPlanId, CuttingLinkStatus status) {
        if (status == CuttingLinkStatus.SINGLE) {
            return Objects.requireNonNull(cuttingPlanId, "cuttingPlanId required for SINGLE");
        }
        if (cuttingPlanId != null) {
            throw new IllegalArgumentException(
                    "cuttingPlanId must be null unless cuttingLinkStatus is SINGLE");
        }
        return null;
    }

    private void validateCuttingReferences() {
        if (cuttingLinkStatus == CuttingLinkStatus.NONE && !cuttingPlanReferences.isEmpty()) {
            throw new IllegalArgumentException(
                    "cuttingPlanReferences must be empty when cuttingLinkStatus is NONE");
        }
        if (cuttingLinkStatus == CuttingLinkStatus.SINGLE) {
            if (cuttingPlanReferences.size() != 1
                    || !cuttingPlanReferences.getFirst().equals(cuttingPlanId)) {
                throw new IllegalArgumentException(
                        "SINGLE cutting link must reference exactly the cuttingPlanId");
            }
        }
        if (cuttingLinkStatus == CuttingLinkStatus.MULTIPLE_REFERENCES
                && cuttingPlanReferences.size() < 2) {
            throw new IllegalArgumentException(
                    "MULTIPLE_REFERENCES requires at least two distinct CuttingPlanId values");
        }
        if (new LinkedHashSet<>(cuttingPlanReferences).size() != cuttingPlanReferences.size()) {
            throw new IllegalArgumentException("cuttingPlanReferences must be unique");
        }
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " must be >= 0: " + value);
        }
        return value;
    }
}
