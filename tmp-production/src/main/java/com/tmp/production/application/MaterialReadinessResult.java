package com.tmp.production.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Side-effect-free Production material readiness snapshot for a planned release quantity set.
 *
 * <p>Not persisted. Compares Release planned demand against current AVAILABLE on the production
 * warehouse only. Does not reserve stock.
 */
public record MaterialReadinessResult(
        MaterialReadinessStatus status,
        MaterialReadinessReason reason,
        int deficientLineCount,
        List<MaterialReadinessLine> lines) {

    public MaterialReadinessResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(lines, "lines");
        if (deficientLineCount < 0) {
            throw new IllegalArgumentException("deficientLineCount must be >= 0");
        }
        lines = List.copyOf(lines);
    }

    public static MaterialReadinessResult notApplicable(MaterialReadinessReason reason) {
        Objects.requireNonNull(reason, "reason");
        return new MaterialReadinessResult(
                MaterialReadinessStatus.NOT_APPLICABLE, reason, 0, List.of());
    }

    public static MaterialReadinessResult noProductionWarehouse() {
        return new MaterialReadinessResult(
                MaterialReadinessStatus.NO_PRODUCTION_WAREHOUSE,
                MaterialReadinessReason.NO_PRODUCTION_WAREHOUSE,
                0,
                List.of());
    }

    public static MaterialReadinessResult unresolvedMaterial() {
        return new MaterialReadinessResult(
                MaterialReadinessStatus.MATERIAL_REFERENCE_UNRESOLVED,
                MaterialReadinessReason.MATERIAL_REFERENCE_UNRESOLVED,
                0,
                List.of());
    }

    public enum MaterialReadinessStatus {
        READY,
        NOT_READY,
        NO_PRODUCTION_WAREHOUSE,
        NOT_APPLICABLE,
        MATERIAL_REFERENCE_UNRESOLVED
    }

    public enum MaterialReadinessReason {
        NONE,
        NOT_ACCEPTED,
        MANUFACTURED,
        CANCELLED,
        NO_RELEASABLE_QUANTITY,
        INSUFFICIENT_STOCK,
        NO_PRODUCTION_WAREHOUSE,
        MATERIAL_REFERENCE_UNRESOLVED
    }

    public record MaterialReadinessLine(
            UUID materialReferenceId,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            BigDecimal requiredQuantity,
            BigDecimal availableQuantity,
            BigDecimal shortageQuantity) {

        public MaterialReadinessLine {
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(materialCode, "materialCode");
            Objects.requireNonNull(materialName, "materialName");
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
            Objects.requireNonNull(requiredQuantity, "requiredQuantity");
            Objects.requireNonNull(availableQuantity, "availableQuantity");
            Objects.requireNonNull(shortageQuantity, "shortageQuantity");
        }

        public boolean deficient() {
            return shortageQuantity.signum() > 0;
        }
    }
}
