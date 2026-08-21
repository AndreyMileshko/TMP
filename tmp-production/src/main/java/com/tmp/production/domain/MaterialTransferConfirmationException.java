package com.tmp.production.domain;

/** Rejected Material Transfer confirmation (allocation / cell / version / result mismatch). */
public final class MaterialTransferConfirmationException extends RuntimeException {

    public MaterialTransferConfirmationException(String message) {
        super(message);
    }

    public MaterialTransferConfirmationException(String message, Throwable cause) {
        super(message, cause);
    }
}
