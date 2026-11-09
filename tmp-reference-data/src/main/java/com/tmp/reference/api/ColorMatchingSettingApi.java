package com.tmp.reference.api;

import java.util.Objects;

/**
 * Public API for the system-wide automatic SO7 color-matching switch (TMP-SPEC-015 sections 7 /
 * 15 / 17).
 *
 * <p>Read access follows section 15.2 ({@code reference.color.view} or the full Order Management
 * import permission set). Mutation requires {@code reference.color-setting.edit}.
 */
public interface ColorMatchingSettingApi {

    /** Returns the current switch state and optimistic-lock version. */
    ColorMatchingSettingDto getAutomaticMatchingSetting();

    /**
     * Sets the switch state. Rejects concurrent updates when {@code expectedVersion} does not match
     * the stored version.
     */
    ColorMatchingSettingDto setAutomaticMatchingEnabled(boolean enabled, long expectedVersion);

    /** Public immutable view of the automatic matching switch. */
    final class ColorMatchingSettingDto {

        private final boolean enabled;
        private final long version;

        public ColorMatchingSettingDto(boolean enabled, long version) {
            this.enabled = enabled;
            this.version = version;
        }

        public boolean enabled() {
            return enabled;
        }

        public long version() {
            return version;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ColorMatchingSettingDto that)) {
                return false;
            }
            return enabled == that.enabled && version == that.version;
        }

        @Override
        public int hashCode() {
            return Objects.hash(enabled, version);
        }

        @Override
        public String toString() {
            return "ColorMatchingSettingDto{enabled=" + enabled + ", version=" + version + "}";
        }
    }
}