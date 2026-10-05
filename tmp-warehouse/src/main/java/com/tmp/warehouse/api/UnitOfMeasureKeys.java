package com.tmp.warehouse.api;

import com.tmp.warehouse.domain.UnitOfMeasure;

/**
 * Public Warehouse unit-of-measure identity comparison for cross-capability matching.
 *
 * <p>Delegates to the single Warehouse canonical rule {@link UnitOfMeasure#equalForKey(String,
 * String)}. Callers must not invent parallel normalize helpers.
 */
public final class UnitOfMeasureKeys {

    private UnitOfMeasureKeys() {}

    /**
     * Comparison-time equality for material identity matching. Does not mutate persisted or display
     * values.
     */
    public static boolean equalForKey(String left, String right) {
        return UnitOfMeasure.equalForKey(left, right);
    }
}
