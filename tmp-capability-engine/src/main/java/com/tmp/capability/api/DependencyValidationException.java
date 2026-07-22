package com.tmp.capability.api;

import java.util.List;
import java.util.Objects;

/**
 * Unchecked exception raised when a Capability's declared dependencies fail validation.
 *
 * <p>Carries a machine-readable {@link Reason} plus the offending {@link CapabilityId}(s)
 * for precise diagnostics, in addition to the standard human-readable message. This type
 * is a pure data/error contract: it performs no graph traversal or validation itself —
 * that logic belongs to the dependency validator that constructs these exceptions.
 */
public final class DependencyValidationException extends RuntimeException {

    /** Machine-readable classification of a dependency validation failure. */
    public enum DependencyValidationReason {
        MISSING_DEPENDENCY,
        SELF_DEPENDENCY,
        DUPLICATE_DEPENDENCY,
        INCOMPATIBLE_VERSION,
        CYCLIC_DEPENDENCY
    }

    private final DependencyValidationReason reason;
    private final List<CapabilityId> offendingIds;

    private DependencyValidationException(
            DependencyValidationReason reason, List<CapabilityId> offendingIds, String message) {
        super(message);
        this.reason = reason;
        this.offendingIds = List.copyOf(offendingIds);
    }

    public static DependencyValidationException missingDependency(CapabilityId owner, CapabilityId missingDependencyId) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(missingDependencyId, "missingDependencyId");
        return new DependencyValidationException(
                DependencyValidationReason.MISSING_DEPENDENCY,
                List.of(owner, missingDependencyId),
                "Capability '" + owner + "' declares a dependency on '" + missingDependencyId
                        + "', which is not present in the known capability set");
    }

    public static DependencyValidationException selfDependency(CapabilityId capabilityId) {
        Objects.requireNonNull(capabilityId, "capabilityId");
        return new DependencyValidationException(
                DependencyValidationReason.SELF_DEPENDENCY,
                List.of(capabilityId),
                "Capability '" + capabilityId + "' declares a dependency on itself");
    }

    public static DependencyValidationException duplicateDependency(CapabilityId owner, CapabilityId duplicatedDependencyId) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(duplicatedDependencyId, "duplicatedDependencyId");
        return new DependencyValidationException(
                DependencyValidationReason.DUPLICATE_DEPENDENCY,
                List.of(owner, duplicatedDependencyId),
                "Capability '" + owner + "' declares a duplicate dependency on '" + duplicatedDependencyId + "'");
    }

    public static DependencyValidationException incompatibleVersion(
            CapabilityId owner,
            CapabilityId dependencyId,
            CapabilityVersion requiredMinimumVersion,
            CapabilityVersion actualVersion) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(dependencyId, "dependencyId");
        Objects.requireNonNull(requiredMinimumVersion, "requiredMinimumVersion");
        Objects.requireNonNull(actualVersion, "actualVersion");
        return new DependencyValidationException(
                DependencyValidationReason.INCOMPATIBLE_VERSION,
                List.of(owner, dependencyId),
                "Capability '" + owner + "' requires '" + dependencyId + "' at minimum version '"
                        + requiredMinimumVersion + "', but the available version is '" + actualVersion + "'");
    }

    public static DependencyValidationException cyclicDependency(List<CapabilityId> cycle) {
        Objects.requireNonNull(cycle, "cycle");
        if (cycle.isEmpty()) {
            throw new IllegalArgumentException("cycle must not be empty");
        }
        return new DependencyValidationException(
                DependencyValidationReason.CYCLIC_DEPENDENCY,
                cycle,
                "Cyclic dependency detected among capabilities: " + cycle);
    }

    public DependencyValidationReason reason() {
        return reason;
    }

    public List<CapabilityId> offendingIds() {
        return offendingIds;
    }
}
