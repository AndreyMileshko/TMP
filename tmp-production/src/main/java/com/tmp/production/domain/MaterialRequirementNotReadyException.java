package com.tmp.production.domain;

import java.util.Objects;

/**
 * Raised when a Material Requirement cannot be prepared because a material identity is unresolved
 * or ambiguous in the Warehouse catalog.
 */
public final class MaterialRequirementNotReadyException extends RuntimeException {

    public enum Problem {
        UNRESOLVED,
        AMBIGUOUS
    }

    private final SourceOrderId sourceOrderId;
    private final SpecificationMaterialIdentity identity;
    private final Problem problem;

    public MaterialRequirementNotReadyException(
            SourceOrderId sourceOrderId,
            SpecificationMaterialIdentity identity,
            Problem problem) {
        super(
                "Material requirement is not ready: "
                        + Objects.requireNonNull(problem, "problem")
                        + " material for order "
                        + sourceOrderId
                        + ", identity="
                        + identity);
        this.sourceOrderId = Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.problem = problem;
    }

    public SourceOrderId sourceOrderId() {
        return sourceOrderId;
    }

    public SpecificationMaterialIdentity identity() {
        return identity;
    }

    public Problem problem() {
        return problem;
    }
}
