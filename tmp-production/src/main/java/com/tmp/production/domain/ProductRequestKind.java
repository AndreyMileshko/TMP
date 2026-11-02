package com.tmp.production.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * Per-source-item classification of a Material Requirement product quantity.
 *
 * <p>{@link #COUNTED} participates in authoritative normal product coverage. {@link #REPEATED} is an
 * additional request that never consumes that coverage. {@link #LEGACY_EXCLUDED} preserves V49
 * historical provenance without inventing coverage or being treated as a repeated request.
 */
public enum ProductRequestKind {
    COUNTED(true),
    REPEATED(false),
    LEGACY_EXCLUDED(false);

    private final boolean countsTowardProductCoverage;

    ProductRequestKind(boolean countsTowardProductCoverage) {
        this.countsTowardProductCoverage = countsTowardProductCoverage;
    }

    public boolean countsTowardProductCoverage() {
        return countsTowardProductCoverage;
    }

    public static ProductRequestKind parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        for (ProductRequestKind kind : values()) {
            if (kind.name().equals(normalized)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown product_request_kind: " + raw);
    }
}