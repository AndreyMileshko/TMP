package com.tmp.production.domain;

/** Validation or orchestration failure for the Release Products use case. */
public final class ReleaseProductsException extends RuntimeException {

    public ReleaseProductsException(String message) {
        super(message);
    }

    public ReleaseProductsException(String message, Throwable cause) {
        super(message, cause);
    }
}
