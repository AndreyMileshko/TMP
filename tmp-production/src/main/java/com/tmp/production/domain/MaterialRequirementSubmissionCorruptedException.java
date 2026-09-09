package com.tmp.production.domain;

/**
 * Raised when a SUBMITTED Material Requirement is missing its mandatory submission links / routing
 * snapshot (Stage 3.5.10 §15). This is a fail-closed corrupted-state signal: the system must NOT
 * reroute or recreate documents; the inconsistency must be investigated instead.
 */
public final class MaterialRequirementSubmissionCorruptedException extends RuntimeException {

    private final transient MaterialRequirementId requirementId;

    public MaterialRequirementSubmissionCorruptedException(
            MaterialRequirementId requirementId, String detail) {
        super(
                "SUBMITTED material requirement "
                        + requirementId
                        + " is in a corrupted submission state: "
                        + detail);
        this.requirementId = requirementId;
    }

    public MaterialRequirementId requirementId() {
        return requirementId;
    }
}
