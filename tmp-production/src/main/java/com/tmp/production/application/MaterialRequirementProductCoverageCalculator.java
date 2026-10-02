package com.tmp.production.application;

import java.util.Objects;

/**
 * Single authoritative calculator for Material Requirement product-quantity coverage.
 *
 * <p>Formula (Stage 7 Phase 2):
 *
 * <pre>
 * outstandingSubmittedCoverage =
 *     max(0, cumulativeSubmittedProductQuantity - releasedQuantity)
 *
 * requestable =
 *     max(0, activeProductionQuantity - outstandingSubmittedCoverage)
 * </pre>
 *
 * <p>Only {@code SUBMITTED} requirements contribute to cumulative submitted coverage. DRAFT never
 * permanently reserves coverage.
 */
public final class MaterialRequirementProductCoverageCalculator {

    public long outstandingSubmittedCoverage(
            long cumulativeSubmittedProductQuantity, long releasedQuantity) {
        requireNonNegative(cumulativeSubmittedProductQuantity, "cumulativeSubmittedProductQuantity");
        requireNonNegative(releasedQuantity, "releasedQuantity");
        return Math.max(0L, cumulativeSubmittedProductQuantity - releasedQuantity);
    }

    public long requestableProductQuantity(
            long activeProductionQuantity,
            long cumulativeSubmittedProductQuantity,
            long releasedQuantity) {
        requireNonNegative(activeProductionQuantity, "activeProductionQuantity");
        long outstanding =
                outstandingSubmittedCoverage(
                        cumulativeSubmittedProductQuantity, releasedQuantity);
        return Math.max(0L, activeProductionQuantity - outstanding);
    }

    public ProductItemCoverage coverage(
            long orderedQuantity,
            long activeProductionQuantity,
            long releasedQuantity,
            long cumulativeSubmittedProductQuantity) {
        requireNonNegative(orderedQuantity, "orderedQuantity");
        long outstanding =
                outstandingSubmittedCoverage(
                        cumulativeSubmittedProductQuantity, releasedQuantity);
        long requestable =
                requestableProductQuantity(
                        activeProductionQuantity,
                        cumulativeSubmittedProductQuantity,
                        releasedQuantity);
        return new ProductItemCoverage(
                orderedQuantity,
                activeProductionQuantity,
                releasedQuantity,
                cumulativeSubmittedProductQuantity,
                outstanding,
                requestable);
    }

    public record ProductItemCoverage(
            long orderedQuantity,
            long activeProductionQuantity,
            long releasedQuantity,
            long submittedProductCoverage,
            long outstandingSubmittedCoverage,
            long requestableProductQuantity) {
        public ProductItemCoverage {
            requireNonNegative(orderedQuantity, "orderedQuantity");
            requireNonNegative(activeProductionQuantity, "activeProductionQuantity");
            requireNonNegative(releasedQuantity, "releasedQuantity");
            requireNonNegative(submittedProductCoverage, "submittedProductCoverage");
            requireNonNegative(outstandingSubmittedCoverage, "outstandingSubmittedCoverage");
            requireNonNegative(requestableProductQuantity, "requestableProductQuantity");
        }
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must be >= 0: " + value);
        }
        Objects.requireNonNull(name, "name");
    }
}
