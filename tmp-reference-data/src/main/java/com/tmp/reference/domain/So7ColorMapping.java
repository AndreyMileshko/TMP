package com.tmp.reference.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Global SO7 color mapping aggregate (TMP-SPEC-015 section 6).
 *
 * <p>One normalized {@link So7SourceKey} identifies at most one mapping row (active or inactive).
 * {@code sourceText} is trimmed and immutable after create. Inside/outside may reference the same
 * catalog color. Physical deletion of a mapping row is allowed and frees the source key; catalog
 * colors themselves are never deleted here.
 */
public final class So7ColorMapping {

    public static final int MAX_SOURCE_TEXT_LENGTH = 64;

    private static final char NBSP = '\u00A0';

    private final UUID id;
    private final String sourceText;
    private final So7SourceKey sourceKey;
    private final ColorId insideColorId;
    private final ColorId outsideColorId;
    private final boolean active;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final UUID createdBy;
    private final UUID updatedBy;

    private So7ColorMapping(
            UUID id,
            String sourceText,
            So7SourceKey sourceKey,
            ColorId insideColorId,
            ColorId outsideColorId,
            boolean active,
            long version,
            Instant createdAt,
            Instant updatedAt,
            UUID createdBy,
            UUID updatedBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.sourceText = Objects.requireNonNull(sourceText, "sourceText");
        this.sourceKey = Objects.requireNonNull(sourceKey, "sourceKey");
        this.insideColorId = Objects.requireNonNull(insideColorId, "insideColorId");
        this.outsideColorId = Objects.requireNonNull(outsideColorId, "outsideColorId");
        this.active = active;
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
    }

    /**
     * Creates a new active mapping.
     *
     * @param rawSourceText original SO7 label (trimmed for storage; spelling of meaningful chars preserved)
     * @param insideColorId catalog color for the inside side
     * @param outsideColorId catalog color for the outside side
     * @param createdBy actor UUID, or {@code null} for system/seed-style creation
     * @param clock clock for timestamps
     */
    public static So7ColorMapping create(
            String rawSourceText,
            ColorId insideColorId,
            ColorId outsideColorId,
            UUID createdBy,
            Clock clock) {
        Objects.requireNonNull(insideColorId, "insideColorId");
        Objects.requireNonNull(outsideColorId, "outsideColorId");
        Objects.requireNonNull(clock, "clock");
        String sourceText = validateAndTrimSourceText(rawSourceText);
        Instant now = clock.instant();
        return new So7ColorMapping(
                UUID.randomUUID(),
                sourceText,
                So7SourceKey.of(sourceText),
                insideColorId,
                outsideColorId,
                true,
                0L,
                now,
                now,
                createdBy,
                createdBy);
    }

    /**
     * Rehydrates a persisted mapping. Used by persistence adapters only.
     */
    public static So7ColorMapping rehydrate(
            UUID id,
            String sourceText,
            So7SourceKey sourceKey,
            ColorId insideColorId,
            ColorId outsideColorId,
            boolean active,
            long version,
            Instant createdAt,
            Instant updatedAt,
            UUID createdBy,
            UUID updatedBy) {
        Objects.requireNonNull(sourceText, "sourceText");
        Objects.requireNonNull(sourceKey, "sourceKey");
        if (!So7SourceKey.of(sourceText).equals(sourceKey)) {
            throw new IllegalArgumentException(
                    "sourceKey must equal So7SourceKey.of(sourceText); sourceText="
                            + sourceText
                            + ", sourceKey="
                            + sourceKey);
        }
        return new So7ColorMapping(
                id,
                sourceText,
                sourceKey,
                insideColorId,
                outsideColorId,
                active,
                version,
                createdAt,
                updatedAt,
                createdBy,
                updatedBy);
    }

    public So7ColorMapping updatePair(
            ColorId insideColorId, ColorId outsideColorId, UUID updatedBy, Clock clock) {
        Objects.requireNonNull(insideColorId, "insideColorId");
        Objects.requireNonNull(outsideColorId, "outsideColorId");
        Objects.requireNonNull(clock, "clock");
        return new So7ColorMapping(
                id,
                sourceText,
                sourceKey,
                insideColorId,
                outsideColorId,
                active,
                version,
                createdAt,
                clock.instant(),
                createdBy,
                updatedBy);
    }

    public So7ColorMapping activate(UUID updatedBy, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        if (active) {
            throw new IllegalStateException("Mapping is already active: " + sourceText);
        }
        return new So7ColorMapping(
                id,
                sourceText,
                sourceKey,
                insideColorId,
                outsideColorId,
                true,
                version,
                createdAt,
                clock.instant(),
                createdBy,
                updatedBy);
    }

    public So7ColorMapping deactivate(UUID updatedBy, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        if (!active) {
            throw new IllegalStateException("Mapping is already inactive: " + sourceText);
        }
        return new So7ColorMapping(
                id,
                sourceText,
                sourceKey,
                insideColorId,
                outsideColorId,
                false,
                version,
                createdAt,
                clock.instant(),
                createdBy,
                updatedBy);
    }

    static String validateAndTrimSourceText(String rawSourceText) {
        if (rawSourceText == null) {
            throw new InvalidSourceException("SO7 source text must not be null");
        }
        String trimmed = trimEdges(rawSourceText);
        if (trimmed.isEmpty()) {
            throw new InvalidSourceException("SO7 source text must not be blank");
        }
        if (trimmed.length() > MAX_SOURCE_TEXT_LENGTH) {
            throw new InvalidSourceException(
                    "SO7 source text length must be <= "
                            + MAX_SOURCE_TEXT_LENGTH
                            + " characters");
        }
        return trimmed;
    }

    private static String trimEdges(String text) {
        int length = text.length();
        int start = 0;
        int end = length - 1;
        while (start <= end && isWhitespace(text.charAt(start))) {
            start++;
        }
        while (end >= start && isWhitespace(text.charAt(end))) {
            end--;
        }
        if (start > end) {
            return "";
        }
        return text.substring(start, end + 1);
    }

    private static boolean isWhitespace(char ch) {
        return Character.isWhitespace(ch) || ch == NBSP;
    }

    public UUID id() {
        return id;
    }

    public String sourceText() {
        return sourceText;
    }

    public So7SourceKey sourceKey() {
        return sourceKey;
    }

    public ColorId insideColorId() {
        return insideColorId;
    }

    public ColorId outsideColorId() {
        return outsideColorId;
    }

    public boolean active() {
        return active;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public UUID createdBy() {
        return createdBy;
    }

    public UUID updatedBy() {
        return updatedBy;
    }

    /** Controlled rejection of an SO7 source label that violates TMP-SPEC-015 section 6.1. */
    public static final class InvalidSourceException extends RuntimeException {

        public InvalidSourceException(String message) {
            super(message);
        }
    }

    /**
     * Controlled rejection when {@code source_key} already exists (unique regardless of activity).
     */
    public static final class DuplicateSourceKeyException extends RuntimeException {

        public DuplicateSourceKeyException(String message) {
            super(message);
        }

        public DuplicateSourceKeyException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Optimistic-lock conflict on mapping update or delete. */
    public static final class OptimisticLockConflictException extends RuntimeException {

        public OptimisticLockConflictException(String message) {
            super(message);
        }
    }
}