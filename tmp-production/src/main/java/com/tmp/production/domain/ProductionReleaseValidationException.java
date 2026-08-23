package com.tmp.production.domain;

/**
 * Raised when Production Release document posting validation fails before state mutation.
 */
public final class ProductionReleaseValidationException extends RuntimeException {

    public ProductionReleaseValidationException(String message) {
        super(message);
    }

    public ProductionReleaseValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
