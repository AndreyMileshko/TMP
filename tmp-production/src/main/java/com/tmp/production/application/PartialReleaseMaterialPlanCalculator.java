package com.tmp.production.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Pure calculator for partial/repeated Production Release material plan (Production Spec v2.3
 * §15.1.1).
 *
 * <p>{@code Q} = frozen {@code Specification.lineQuantity} on the whole Order Item. {@code N} =
 * ordered quantity. Uses cumulative proportional allocation; final release closes exact {@code Q}.
 */
public final class PartialReleaseMaterialPlanCalculator {

    public static final int SCALE = 6;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public record Input(
            BigDecimal lineQuantity,
            long orderedQuantity,
            long releasedBefore,
            long releaseCurrent) {

        public Input {
            Objects.requireNonNull(lineQuantity, "lineQuantity");
            if (orderedQuantity <= 0) {
                throw new IllegalArgumentException("orderedQuantity must be > 0");
            }
            if (releasedBefore < 0 || releaseCurrent <= 0) {
                throw new IllegalArgumentException("invalid release quantities");
            }
            if (releasedBefore + releaseCurrent > orderedQuantity) {
                throw new IllegalArgumentException("release exceeds ordered quantity");
            }
        }
    }

    public record LinePlan(BigDecimal planCurrent, BigDecimal cumulativeAfter) {

        public LinePlan {
            Objects.requireNonNull(planCurrent, "planCurrent");
            Objects.requireNonNull(cumulativeAfter, "cumulativeAfter");
        }
    }

    public LinePlan calculate(Input input) {
        BigDecimal q = input.lineQuantity();
        long n = input.orderedQuantity();
        long rBefore = input.releasedBefore();
        long rCurrent = input.releaseCurrent();
        long rAfter = rBefore + rCurrent;

        BigDecimal cBefore = cumulativeTarget(q, n, rBefore);
        BigDecimal cAfter =
                rAfter == n ? q.setScale(SCALE, ROUNDING) : cumulativeTarget(q, n, rAfter);
        BigDecimal plan = cAfter.subtract(cBefore).setScale(SCALE, ROUNDING);
        return new LinePlan(plan, cAfter);
    }

    public static BigDecimal normalize(BigDecimal value) {
        Objects.requireNonNull(value, "value");
        return value.setScale(SCALE, ROUNDING);
    }

    private static BigDecimal cumulativeTarget(BigDecimal q, long n, long released) {
        if (released == 0) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }
        if (released == n) {
            return q.setScale(SCALE, ROUNDING);
        }
        return normalize(
                q.multiply(BigDecimal.valueOf(released))
                        .divide(BigDecimal.valueOf(n), SCALE, ROUNDING));
    }
}
