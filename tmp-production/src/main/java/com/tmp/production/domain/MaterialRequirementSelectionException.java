package com.tmp.production.domain;

import java.util.Objects;

/**
 * Raised when Material Requirement preparation receives an invalid item selection (empty, wrong
 * order, or non-eligible status).
 */
public final class MaterialRequirementSelectionException extends RuntimeException {

    public MaterialRequirementSelectionException(String message) {
        super(Objects.requireNonNull(message, "message"));
    }
}
