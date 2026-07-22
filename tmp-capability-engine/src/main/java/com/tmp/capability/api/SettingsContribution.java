package com.tmp.capability.api;

import java.util.Objects;

/**
 * Immutable, pure-data descriptor of a setting that a Capability declares it exposes.
 * This is a registration-only descriptor: it carries no persistence mechanism and no
 * runtime value storage; a full settings UI/persistence layer is out of scope for the
 * Capability Engine unless a future stage specifies it.
 */
public final class SettingsContribution {

    private final String settingKey;
    private final String displayName;
    private final String description;
    private final String defaultValue;

    private SettingsContribution(String settingKey, String displayName, String description, String defaultValue) {
        this.settingKey = settingKey;
        this.displayName = displayName;
        this.description = description;
        this.defaultValue = defaultValue;
    }

    public static SettingsContribution of(
            String settingKey, String displayName, String description, String defaultValue) {
        requireNonBlank(settingKey, "settingKey");
        requireNonBlank(displayName, "displayName");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(defaultValue, "defaultValue");
        return new SettingsContribution(settingKey, displayName, description, defaultValue);
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    public String settingKey() {
        return settingKey;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public String defaultValue() {
        return defaultValue;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SettingsContribution that)) {
            return false;
        }
        return settingKey.equals(that.settingKey);
    }

    @Override
    public int hashCode() {
        return settingKey.hashCode();
    }

    @Override
    public String toString() {
        return settingKey;
    }
}
