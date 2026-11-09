package com.tmp.reference.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Public immutable view of an inside/outside TMP color pair with computed display color
 * (TMP-SPEC-015 sections 6.3 / 17).
 */
public final class ResolvedColorPair {

    private final UUID insideColorId;
    private final UUID outsideColorId;
    private final String insideCode;
    private final String outsideCode;
    private final String displayColor;

    public ResolvedColorPair(
            UUID insideColorId,
            UUID outsideColorId,
            String insideCode,
            String outsideCode,
            String displayColor) {
        this.insideColorId = Objects.requireNonNull(insideColorId, "insideColorId");
        this.outsideColorId = Objects.requireNonNull(outsideColorId, "outsideColorId");
        this.insideCode = Objects.requireNonNull(insideCode, "insideCode");
        this.outsideCode = Objects.requireNonNull(outsideCode, "outsideCode");
        this.displayColor = Objects.requireNonNull(displayColor, "displayColor");
    }

    public UUID insideColorId() {
        return insideColorId;
    }

    public UUID outsideColorId() {
        return outsideColorId;
    }

    public String insideCode() {
        return insideCode;
    }

    public String outsideCode() {
        return outsideCode;
    }

    public String displayColor() {
        return displayColor;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ResolvedColorPair that)) {
            return false;
        }
        return insideColorId.equals(that.insideColorId)
                && outsideColorId.equals(that.outsideColorId)
                && insideCode.equals(that.insideCode)
                && outsideCode.equals(that.outsideCode)
                && displayColor.equals(that.displayColor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(insideColorId, outsideColorId, insideCode, outsideCode, displayColor);
    }

    @Override
    public String toString() {
        return "ResolvedColorPair{insideCode="
                + insideCode
                + ", outsideCode="
                + outsideCode
                + ", displayColor="
                + displayColor
                + "}";
    }
}
