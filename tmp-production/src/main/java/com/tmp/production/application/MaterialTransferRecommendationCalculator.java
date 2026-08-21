package com.tmp.production.application;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Pure calculator for MAIN → PRODUCTION transfer recommendation quantities.
 *
 * <p>Uses the same planned {@code requiredQuantity} as Material Availability Check; does not
 * recompute Specification independently.
 */
public final class MaterialTransferRecommendationCalculator {

    public record Recommendation(
            BigDecimal recommendedTransferQuantity, BigDecimal uncoveredDeficit) {

        public Recommendation {
            Objects.requireNonNull(recommendedTransferQuantity, "recommendedTransferQuantity");
            Objects.requireNonNull(uncoveredDeficit, "uncoveredDeficit");
            if (recommendedTransferQuantity.signum() < 0) {
                throw new IllegalArgumentException("recommendedTransferQuantity must be >= 0");
            }
            if (uncoveredDeficit.signum() < 0) {
                throw new IllegalArgumentException("uncoveredDeficit must be >= 0");
            }
        }
    }

    /**
     * recommendedTransfer = min(max(required - productionAvailable, 0), mainAvailable)
     *
     * <p>uncoveredDeficit = max(max(required - productionAvailable, 0) - mainAvailable, 0)
     */
    public Recommendation calculate(
            BigDecimal requiredQuantity,
            BigDecimal productionWarehouseAvailable,
            BigDecimal mainWarehouseAvailable) {
        Objects.requireNonNull(requiredQuantity, "requiredQuantity");
        Objects.requireNonNull(productionWarehouseAvailable, "productionWarehouseAvailable");
        Objects.requireNonNull(mainWarehouseAvailable, "mainWarehouseAvailable");
        if (requiredQuantity.signum() < 0
                || productionWarehouseAvailable.signum() < 0
                || mainWarehouseAvailable.signum() < 0) {
            throw new IllegalArgumentException("quantities must be >= 0");
        }

        BigDecimal needToProduction =
                requiredQuantity.subtract(productionWarehouseAvailable).max(BigDecimal.ZERO);
        BigDecimal recommended = needToProduction.min(mainWarehouseAvailable);
        BigDecimal uncovered = needToProduction.subtract(mainWarehouseAvailable).max(BigDecimal.ZERO);
        return new Recommendation(recommended, uncovered);
    }
}
