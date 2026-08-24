package com.tmp.production.domain;

/** Validation or orchestration failure for the Cancel Order Production use case. */
public final class CancelOrderProductionException extends RuntimeException {

    public CancelOrderProductionException(String message) {
        super(message);
    }

    public CancelOrderProductionException(String message, Throwable cause) {
        super(message, cause);
    }
}
