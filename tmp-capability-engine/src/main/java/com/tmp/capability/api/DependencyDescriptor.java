package com.tmp.capability.api;

import java.util.Objects;

/**
 * Immutable, pure-data declaration that a Capability depends on another Capability
 * identified by {@link #dependencyId()}, requiring at least {@link #minimumVersion()}.
 *
 * <p>This type carries no business meaning and performs no validation beyond null
 * checks; graph-level rules (missing/duplicate/cyclic/self dependency, version
 * incompatibility) are enforced elsewhere against a fully known dependency set.
 */
public final class DependencyDescriptor {

    private final CapabilityId dependencyId;
    private final CapabilityVersion minimumVersion;

    private DependencyDescriptor(CapabilityId dependencyId, CapabilityVersion minimumVersion) {
        this.dependencyId = dependencyId;
        this.minimumVersion = minimumVersion;
    }

    public static DependencyDescriptor of(CapabilityId dependencyId, CapabilityVersion minimumVersion) {
        Objects.requireNonNull(dependencyId, "dependencyId");
        Objects.requireNonNull(minimumVersion, "minimumVersion");
        return new DependencyDescriptor(dependencyId, minimumVersion);
    }

    public CapabilityId dependencyId() {
        return dependencyId;
    }

    public CapabilityVersion minimumVersion() {
        return minimumVersion;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DependencyDescriptor that)) {
            return false;
        }
        return dependencyId.equals(that.dependencyId) && minimumVersion.equals(that.minimumVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dependencyId, minimumVersion);
    }

    @Override
    public String toString() {
        return dependencyId + " >= " + minimumVersion;
    }
}
