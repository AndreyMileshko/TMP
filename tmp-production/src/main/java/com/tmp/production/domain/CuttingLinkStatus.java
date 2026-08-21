package com.tmp.production.domain;

/**
 * Traceability of Cutting Plan references for an aggregated transfer template line.
 *
 * <p>Does not change planning source or recommended quantity.
 */
public enum CuttingLinkStatus {
    /** No Cutting Plan link on contributing Production Items for this material. */
    NONE,
    /** All contributing items reference the same CuttingPlanId. */
    SINGLE,
    /** Contributing items reference more than one distinct CuttingPlanId. */
    MULTIPLE_REFERENCES
}
