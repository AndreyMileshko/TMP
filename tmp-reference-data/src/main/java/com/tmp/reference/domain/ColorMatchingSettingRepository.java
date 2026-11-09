package com.tmp.reference.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Persistence port for the singleton automatic color-matching setting ({@code
 * reference.color_matching_settings}, TMP-SPEC-015 sections 7 / 16.3).
 *
 * <p>Exactly one row with {@code id = 1} is expected after seed (V59).
 */
public interface ColorMatchingSettingRepository {

    /** Loads the singleton setting row. */
    ColorMatchingSetting get();

    /**
     * Persists {@code automatic_matching_enabled} / audit fields with optimistic locking. Uses
     * {@link ColorMatchingSetting#version()} as the expected version; on success returns the row
     * with version incremented by one.
     */
    ColorMatchingSetting update(ColorMatchingSetting setting);

    /**
     * Immutable singleton setting row. Nested here to keep COL-022A within the five-file production
     * budget.
     */
    final class ColorMatchingSetting {

        public static final short SINGLETON_ID = 1;

        private final boolean automaticMatchingEnabled;
        private final long version;
        private final Instant createdAt;
        private final Instant updatedAt;
        private final UUID createdBy;
        private final UUID updatedBy;

        private ColorMatchingSetting(
                boolean automaticMatchingEnabled,
                long version,
                Instant createdAt,
                Instant updatedAt,
                UUID createdBy,
                UUID updatedBy) {
            this.automaticMatchingEnabled = automaticMatchingEnabled;
            this.version = version;
            this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
            this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
            this.createdBy = createdBy;
            this.updatedBy = updatedBy;
        }

        /** Rehydrates a persisted row. Used by persistence adapters only. */
        public static ColorMatchingSetting rehydrate(
                boolean automaticMatchingEnabled,
                long version,
                Instant createdAt,
                Instant updatedAt,
                UUID createdBy,
                UUID updatedBy) {
            return new ColorMatchingSetting(
                    automaticMatchingEnabled,
                    version,
                    createdAt,
                    updatedAt,
                    createdBy,
                    updatedBy);
        }

        /**
         * Returns a copy with the new enabled flag and updated audit fields. Version is unchanged;
         * the repository increments it on successful update.
         */
        public ColorMatchingSetting withEnabled(boolean enabled, UUID updatedByActor, Clock clock) {
            Objects.requireNonNull(clock, "clock");
            return new ColorMatchingSetting(
                    enabled,
                    version,
                    createdAt,
                    clock.instant(),
                    createdBy,
                    updatedByActor);
        }

        public boolean automaticMatchingEnabled() {
            return automaticMatchingEnabled;
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
    }

    /** Thrown when the expected optimistic-lock version does not match the stored row. */
    final class OptimisticLockConflictException extends RuntimeException {

        public OptimisticLockConflictException(String message) {
            super(message);
        }
    }
}