package com.tmp.reference.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * TMP catalog color aggregate (TMP-SPEC-015 section 5).
 *
 * <p>A catalog color is always a single color code. Combinations such as {@code Белый/RAL7016} are
 * not catalog entries. Saved codes are immutable; physical deletion is forbidden; archive /
 * restore changes only {@code active}.
 */
public final class Color {

    public static final int MAX_CODE_LENGTH = 31;

    private final ColorId id;
    private final String code;
    private final ColorCodeKey codeKey;
    private final boolean active;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final UUID createdBy;
    private final UUID updatedBy;

    private Color(
            ColorId id,
            String code,
            ColorCodeKey codeKey,
            boolean active,
            long version,
            Instant createdAt,
            Instant updatedAt,
            UUID createdBy,
            UUID updatedBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.code = Objects.requireNonNull(code, "code");
        this.codeKey = Objects.requireNonNull(codeKey, "codeKey");
        this.active = active;
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
    }

    /**
     * Creates a new active catalog color.
     *
     * @param rawCode user-facing code (trimmed; spelling preserved after trim)
     * @param createdBy actor UUID, or {@code null} for system seed-style creation
     * @param clock clock for timestamps
     */
    public static Color create(String rawCode, UUID createdBy, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        String code = validateAndNormalizeCode(rawCode);
        Instant now = clock.instant();
        return new Color(
                ColorId.generate(),
                code,
                ColorCodeKey.of(code),
                true,
                0L,
                now,
                now,
                createdBy,
                createdBy);
    }

    /**
     * Rehydrates a persisted color. Used by persistence adapters only.
     */
    public static Color rehydrate(
            ColorId id,
            String code,
            ColorCodeKey codeKey,
            boolean active,
            long version,
            Instant createdAt,
            Instant updatedAt,
            UUID createdBy,
            UUID updatedBy) {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(codeKey, "codeKey");
        if (!ColorCodeKey.of(code).equals(codeKey)) {
            throw new IllegalArgumentException(
                    "codeKey must equal ColorCodeKey.of(code); code=" + code + ", codeKey=" + codeKey);
        }
        return new Color(
                id, code, codeKey, active, version, createdAt, updatedAt, createdBy, updatedBy);
    }

    public Color archive(UUID updatedBy, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        if (!active) {
            throw new IllegalStateException("Color is already archived: " + code);
        }
        return new Color(
                id,
                code,
                codeKey,
                false,
                version,
                createdAt,
                clock.instant(),
                createdBy,
                updatedBy);
    }

    public Color restore(UUID updatedBy, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        if (active) {
            throw new IllegalStateException("Color is already active: " + code);
        }
        return new Color(
                id,
                code,
                codeKey,
                true,
                version,
                createdAt,
                clock.instant(),
                createdBy,
                updatedBy);
    }

    static String validateAndNormalizeCode(String rawCode) {
        if (rawCode == null) {
            throw new InvalidCodeException("Color code must not be null");
        }
        String code = rawCode.trim();
        if (code.isEmpty()) {
            throw new InvalidCodeException("Color code must not be blank");
        }
        if (code.length() > MAX_CODE_LENGTH) {
            throw new InvalidCodeException(
                    "Color code length must be <= " + MAX_CODE_LENGTH + " characters");
        }
        int pipeCount = 0;
        int pipeIndex = -1;
        for (int i = 0; i < code.length(); i++) {
            char ch = code.charAt(i);
            if (ch == '/' || ch == '\\') {
                throw new InvalidCodeException(
                        "Color code must not contain '/' or '\\': " + code);
            }
            if (ch == '|') {
                pipeCount++;
                pipeIndex = i;
            }
        }
        if (pipeCount > 1) {
            throw new InvalidCodeException(
                    "Color code must contain at most one '|': " + code);
        }
        if (pipeCount == 1 && (pipeIndex == 0 || pipeIndex == code.length() - 1)) {
            throw new InvalidCodeException(
                    "Color code parts before and after '|' must be non-empty: " + code);
        }
        return code;
    }

    public ColorId id() {
        return id;
    }

    public String code() {
        return code;
    }

    public ColorCodeKey codeKey() {
        return codeKey;
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

    /** Controlled rejection of a color code that violates TMP-SPEC-015 section 5.2. */
    public static final class InvalidCodeException extends RuntimeException {

        public InvalidCodeException(String message) {
            super(message);
        }
    }

    /**
     * Controlled rejection when {@code code_key} already exists (case-insensitive uniqueness).
     */
    public static final class DuplicateCodeException extends RuntimeException {

        public DuplicateCodeException(String message) {
            super(message);
        }

        public DuplicateCodeException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Optimistic-lock conflict on color update. */
    public static final class OptimisticLockConflictException extends RuntimeException {

        public OptimisticLockConflictException(String message) {
            super(message);
        }
    }
}
