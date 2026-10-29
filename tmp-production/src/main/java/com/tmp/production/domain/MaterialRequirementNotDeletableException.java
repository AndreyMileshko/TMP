package com.tmp.production.domain;

import java.util.Objects;

/**
 * Raised when a Material Requirement cannot be physically deleted (wrong status, downstream
 * references, or concurrent mutation).
 */
public final class MaterialRequirementNotDeletableException extends RuntimeException {

    private final MaterialRequirementId requirementId;

    public MaterialRequirementNotDeletableException(
            MaterialRequirementId requirementId, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.requirementId = Objects.requireNonNull(requirementId, "requirementId");
    }

    public MaterialRequirementId requirementId() {
        return requirementId;
    }
}