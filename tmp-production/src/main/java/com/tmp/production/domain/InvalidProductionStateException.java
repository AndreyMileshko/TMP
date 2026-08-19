package com.tmp.production.domain;

/**
 * Raised when a Production domain invariant or state transition rule is violated.
 */
public final class InvalidProductionStateException extends RuntimeException {

    public InvalidProductionStateException(String message) {
        super(message);
    }
}
