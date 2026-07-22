package com.tmp.capability.api;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable technical version identifier for a Capability, in {@code MAJOR.MINOR.PATCH}
 * form (non-negative integers only; no pre-release/build metadata).
 *
 * <p><b>Compatibility rule</b> (minimal, domain-independent technical contract; no
 * external SemVer library is used): an actual version is compatible with a required
 * minimum version if and only if the major components are equal, and the actual
 * minor/patch is greater than or equal to the required minor/patch. A different major
 * version is always incompatible, treated as a breaking-change boundary. This rule is
 * intentionally simple; it is hidden behind {@link #isCompatibleWith(CapabilityVersion)}
 * so it can be revisited later without changing the public shape of this type.
 */
public final class CapabilityVersion implements Comparable<CapabilityVersion> {

    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

    private final int major;
    private final int minor;
    private final int patch;
    private final String raw;

    private CapabilityVersion(int major, int minor, int patch, String raw) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.raw = raw;
    }

    public static CapabilityVersion of(String value) {
        Objects.requireNonNull(value, "value");
        var matcher = VERSION_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Capability version must be in MAJOR.MINOR.PATCH form with non-negative integers: " + value);
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = Integer.parseInt(matcher.group(3));
        return new CapabilityVersion(major, minor, patch, value);
    }

    public int major() {
        return major;
    }

    public int minor() {
        return minor;
    }

    public int patch() {
        return patch;
    }

    /**
     * Returns {@code true} if this (actual) version satisfies the given required
     * minimum version per the rule documented in the class Javadoc.
     */
    public boolean isCompatibleWith(CapabilityVersion required) {
        Objects.requireNonNull(required, "required");
        if (this.major != required.major) {
            return false;
        }
        if (this.minor != required.minor) {
            return this.minor > required.minor;
        }
        return this.patch >= required.patch;
    }

    @Override
    public int compareTo(CapabilityVersion other) {
        int majorCompare = Integer.compare(this.major, other.major);
        if (majorCompare != 0) {
            return majorCompare;
        }
        int minorCompare = Integer.compare(this.minor, other.minor);
        if (minorCompare != 0) {
            return minorCompare;
        }
        return Integer.compare(this.patch, other.patch);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CapabilityVersion that)) {
            return false;
        }
        return major == that.major && minor == that.minor && patch == that.patch;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch);
    }

    @Override
    public String toString() {
        return raw;
    }
}
