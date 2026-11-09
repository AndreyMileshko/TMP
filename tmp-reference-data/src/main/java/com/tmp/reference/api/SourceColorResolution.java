package com.tmp.reference.api;

import java.util.Objects;

/**
 * Resolution outcome for one non-empty SO7 source color text (TMP-SPEC-015 sections 6.2 / 17).
 *
 * <p>{@link Status#MATCHED} carries an active mapping whose inside and outside catalog colors are
 * both active. {@link Status#MAPPING_INACTIVE} covers an existing mapping that cannot be used
 * (inactive mapping and/or inactive referenced colors). {@link Status#NO_MAPPING} means no mapping
 * row exists for the normalized key.
 */
public final class SourceColorResolution {

    /** Outcome of looking up a normalized SO7 source key. */
    public enum Status {
        MATCHED,
        MAPPING_INACTIVE,
        NO_MAPPING
    }

    private final String sourceText;
    private final String sourceKey;
    private final Status status;
    private final ResolvedColorPair pair;

    private SourceColorResolution(
            String sourceText, String sourceKey, Status status, ResolvedColorPair pair) {
        this.sourceText = Objects.requireNonNull(sourceText, "sourceText");
        this.sourceKey = Objects.requireNonNull(sourceKey, "sourceKey");
        this.status = Objects.requireNonNull(status, "status");
        this.pair = pair;
    }

    /** Active mapping with two active catalog colors. */
    public static SourceColorResolution matched(
            String sourceText, String sourceKey, ResolvedColorPair pair) {
        return new SourceColorResolution(
                sourceText, sourceKey, Status.MATCHED, Objects.requireNonNull(pair, "pair"));
    }

    /** Mapping exists but is inactive or references inactive/missing colors. */
    public static SourceColorResolution mappingInactive(String sourceText, String sourceKey) {
        return new SourceColorResolution(sourceText, sourceKey, Status.MAPPING_INACTIVE, null);
    }

    /** No mapping row for the normalized key. */
    public static SourceColorResolution noMapping(String sourceText, String sourceKey) {
        return new SourceColorResolution(sourceText, sourceKey, Status.NO_MAPPING, null);
    }

    /** Original non-empty source text as supplied by the caller (spelling preserved). */
    public String sourceText() {
        return sourceText;
    }

    /** Normalized SO7 key (TMP-SPEC-015 section 6.2). */
    public String sourceKey() {
        return sourceKey;
    }

    public Status status() {
        return status;
    }

    /**
     * Matched pair when {@link #status()} is {@link Status#MATCHED}; otherwise {@code null}.
     */
    public ResolvedColorPair pair() {
        return pair;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SourceColorResolution that)) {
            return false;
        }
        return status == that.status
                && sourceText.equals(that.sourceText)
                && sourceKey.equals(that.sourceKey)
                && Objects.equals(pair, that.pair);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceText, sourceKey, status, pair);
    }

    @Override
    public String toString() {
        return "SourceColorResolution{sourceText="
                + sourceText
                + ", sourceKey="
                + sourceKey
                + ", status="
                + status
                + ", pair="
                + pair
                + "}";
    }
}
