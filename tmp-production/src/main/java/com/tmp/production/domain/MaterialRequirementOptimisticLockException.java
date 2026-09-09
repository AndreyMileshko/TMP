package com.tmp.production.domain;

/** Raised when a concurrent edit updates a Material Requirement with a stale version. */
public final class MaterialRequirementOptimisticLockException extends RuntimeException {

    private final MaterialRequirementId requirementId;
    private final long expectedVersion;

    public MaterialRequirementOptimisticLockException(
            MaterialRequirementId requirementId, long expectedVersion) {
        super(
                "Optimistic lock failure for material requirement "
                        + requirementId
                        + ", expectedVersion="
                        + expectedVersion);
        this.requirementId = requirementId;
        this.expectedVersion = expectedVersion;
    }

    public MaterialRequirementId requirementId() {
        return requirementId;
    }

    public long expectedVersion() {
        return expectedVersion;
    }
}
