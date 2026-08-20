package com.tmp.production.application.port;

import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Port for resolving Order Management specifications via public Query API only.
 *
 * <p>{@link #resolveCurrentForLaunch} is permitted exclusively at Launch.
 * Post-launch operations must use {@link #resolveById} with the frozen {@link SpecificationId}.
 */
public interface OrderSpecificationQueryPort {

    /**
     * Resolves the current immutable specification for Launch freeze.
     * Must not be used after Launch.
     */
    Optional<ResolvedSpecification> resolveCurrentForLaunch(SourceOrderItemId sourceOrderItemId);

    /**
     * Resolves an immutable specification by its stable opaque identifier.
     * This is the only permitted resolution path after Launch.
     */
    Optional<ResolvedSpecification> resolveById(SpecificationId specificationId);

    /** Read-only specification view mapped from OM Public Query. */
    record ResolvedSpecification(
            SpecificationId specificationId,
            SourceOrderItemId sourceOrderItemId,
            BigDecimal orderedQuantity,
            List<ResolvedMaterialLine> materialLines) {

        public ResolvedSpecification {
            Objects.requireNonNull(specificationId, "specificationId");
            Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
            Objects.requireNonNull(orderedQuantity, "orderedQuantity");
            Objects.requireNonNull(materialLines, "materialLines");
            materialLines = List.copyOf(materialLines);
        }
    }

    record ResolvedMaterialLine(
            String materialCode,
            String materialName,
            String color,
            BigDecimal lengthMm,
            BigDecimal lineQuantity,
            String unitOfMeasure) {

        public ResolvedMaterialLine {
            Objects.requireNonNull(materialCode, "materialCode");
            Objects.requireNonNull(lineQuantity, "lineQuantity");
            Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
        }
    }
}
