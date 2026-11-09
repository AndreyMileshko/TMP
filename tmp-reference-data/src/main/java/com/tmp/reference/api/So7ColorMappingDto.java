package com.tmp.reference.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Public immutable view of a global SO7 color mapping (TMP-SPEC-015 section 17).
 *
 * <p>Does not expose persistence entities or mutable domain internals. Pair codes and display color
 * are computed at read time.
 */
public final class So7ColorMappingDto {

    /** Active-status filter for {@link So7ColorMappingQueryApi#listMappings}. */
    public enum ActiveFilter {
        ACTIVE,
        INACTIVE,
        ALL
    }

    private final UUID id;
    private final String sourceText;
    private final String sourceKey;
    private final UUID insideColorId;
    private final UUID outsideColorId;
    private final String insideCode;
    private final String outsideCode;
    private final boolean insideActive;
    private final boolean outsideActive;
    private final String displayColor;
    private final boolean active;
    private final long version;

    public So7ColorMappingDto(
            UUID id,
            String sourceText,
            String sourceKey,
            UUID insideColorId,
            UUID outsideColorId,
            String insideCode,
            String outsideCode,
            boolean insideActive,
            boolean outsideActive,
            String displayColor,
            boolean active,
            long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.sourceText = Objects.requireNonNull(sourceText, "sourceText");
        this.sourceKey = Objects.requireNonNull(sourceKey, "sourceKey");
        this.insideColorId = Objects.requireNonNull(insideColorId, "insideColorId");
        this.outsideColorId = Objects.requireNonNull(outsideColorId, "outsideColorId");
        this.insideCode = Objects.requireNonNull(insideCode, "insideCode");
        this.outsideCode = Objects.requireNonNull(outsideCode, "outsideCode");
        this.insideActive = insideActive;
        this.outsideActive = outsideActive;
        this.displayColor = Objects.requireNonNull(displayColor, "displayColor");
        this.active = active;
        this.version = version;
    }

    public UUID id() {
        return id;
    }

    public String sourceText() {
        return sourceText;
    }

    public String sourceKey() {
        return sourceKey;
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

    public boolean insideActive() {
        return insideActive;
    }

    public boolean outsideActive() {
        return outsideActive;
    }

    public String displayColor() {
        return displayColor;
    }

    public boolean active() {
        return active;
    }

    public long version() {
        return version;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof So7ColorMappingDto that)) {
            return false;
        }
        return active == that.active
                && insideActive == that.insideActive
                && outsideActive == that.outsideActive
                && version == that.version
                && id.equals(that.id)
                && sourceText.equals(that.sourceText)
                && sourceKey.equals(that.sourceKey)
                && insideColorId.equals(that.insideColorId)
                && outsideColorId.equals(that.outsideColorId)
                && insideCode.equals(that.insideCode)
                && outsideCode.equals(that.outsideCode)
                && displayColor.equals(that.displayColor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                sourceText,
                sourceKey,
                insideColorId,
                outsideColorId,
                insideCode,
                outsideCode,
                insideActive,
                outsideActive,
                displayColor,
                active,
                version);
    }

    @Override
    public String toString() {
        return "So7ColorMappingDto{id="
                + id
                + ", sourceText="
                + sourceText
                + ", sourceKey="
                + sourceKey
                + ", insideCode="
                + insideCode
                + ", outsideCode="
                + outsideCode
                + ", displayColor="
                + displayColor
                + ", active="
                + active
                + ", version="
                + version
                + "}";
    }
}