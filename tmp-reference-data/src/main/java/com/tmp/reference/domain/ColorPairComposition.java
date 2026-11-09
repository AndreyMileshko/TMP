package com.tmp.reference.domain;

import java.util.Objects;

/**
 * Computes the display string for an inside/outside TMP color pair (TMP-SPEC-015 section 6.3).
 *
 * <p>Same codes collapse to a single code; different codes become {@code inside/outside}. Order is
 * always inside then outside. Only Reference Data forms this string.
 */
public final class ColorPairComposition {

    private ColorPairComposition() {}

    /**
     * @param insideCode preserved catalog code for the inside side (not null)
     * @param outsideCode preserved catalog code for the outside side (not null)
     * @return single code when equal; otherwise {@code insideCode + "/" + outsideCode}
     */
    public static String compose(String insideCode, String outsideCode) {
        Objects.requireNonNull(insideCode, "insideCode");
        Objects.requireNonNull(outsideCode, "outsideCode");
        if (insideCode.equals(outsideCode)) {
            return insideCode;
        }
        return insideCode + "/" + outsideCode;
    }
}