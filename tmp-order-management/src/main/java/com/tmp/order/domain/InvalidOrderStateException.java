package com.tmp.order.domain;

/**
 * Thrown when a Customer Order or Order Item status transition is not allowed by Stage 5 rules.
 */
public final class InvalidOrderStateException extends RuntimeException {

    public InvalidOrderStateException(String message) {
        super(message);
    }
}
