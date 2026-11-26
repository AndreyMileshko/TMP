package com.tmp.order.api.imports;

import java.util.Objects;

/**
 * Read-only public contract for final Item Specification color constraints (TMP-SPEC-015 section 10,
 * ADR-039 point 7, Order Management Specification section 28.5).
 *
 * <p>Additive: does not change STXT parsing, {@code OrderImportValidator}, preview/confirm timing,
 * or {@code SpecificationLine} persistence. Callers (import color resolution) check candidate final
 * color strings before preview when automatic matching is ON.
 */
public interface SpecificationColorConstraint {

    /** Maximum length of a final color string after trim ({@code VARCHAR(64)} / domain limit). */
    int MAX_COLOR_LENGTH = 64;

    /**
     * Checks a candidate final color value against Order Management rules.
     *
     * <p>Evaluation uses {@code trim}. Empty after trim -> {@link RejectionReason#BLANK}; length
     * after trim &gt; {@link #MAX_COLOR_LENGTH} -> {@link RejectionReason#TOO_LONG}; forbidden
     * commercial placeholders (case-insensitive) -> {@link RejectionReason#PLACEHOLDER}.
     *
     * @param colorValue candidate final color; {@code null} is rejected as blank
     */
    Result check(String colorValue);

    /** Why a candidate final color was rejected. */
    enum RejectionReason {
        BLANK,
        TOO_LONG,
        PLACEHOLDER
    }

    /** Outcome of {@link SpecificationColorConstraint#check(String)}. */
    final class Result {

        private final boolean allowed;
        private final RejectionReason rejectionReason;

        private Result(boolean allowed, RejectionReason rejectionReason) {
            this.allowed = allowed;
            this.rejectionReason = rejectionReason;
        }

        public static Result allowed() {
            return new Result(true, null);
        }

        public static Result rejected(RejectionReason reason) {
            Objects.requireNonNull(reason, "reason");
            return new Result(false, reason);
        }

        public boolean isAllowed() {
            return allowed;
        }

        /**
         * Rejection code when {@link #isAllowed()} is {@code false}; {@code null} when allowed.
         */
        public RejectionReason rejectionReason() {
            return rejectionReason;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Result that)) {
                return false;
            }
            return allowed == that.allowed && rejectionReason == that.rejectionReason;
        }

        @Override
        public int hashCode() {
            return Objects.hash(allowed, rejectionReason);
        }

        @Override
        public String toString() {
            return allowed
                    ? "SpecificationColorConstraint.Result[allowed]"
                    : "SpecificationColorConstraint.Result[rejected=" + rejectionReason + "]";
        }
    }
}