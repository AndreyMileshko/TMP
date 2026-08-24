package com.tmp.production.domain;

/** Validation failure during Production Cancellation document processing. */
public final class ProductionCancellationValidationException extends RuntimeException {

    public ProductionCancellationValidationException(String message) {
        super(message);
    }

    public ProductionCancellationValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
