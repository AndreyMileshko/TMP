package com.tmp.production.domain;

/**
 * Per-order Production setting that controls how much of the remaining quantity a future
 * Production action (material request, product release) covers.
 *
 * <p>One value applies to both actions. Independent of {@link ProductionStatus}; changing it never
 * alters already recorded releases or material requirements.
 */
public enum ProductionQuantityMode {
    /** The action always covers the whole permitted remaining quantity of the selected item. */
    STANDARD,
    /** The user may enter a quantity below, but never above, the permitted remaining quantity. */
    FLEXIBLE;

    public static ProductionQuantityMode defaultMode() {
        return STANDARD;
    }
}
