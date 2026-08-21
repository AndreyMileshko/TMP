package com.tmp.production.domain;

/**
 * Rejected Production receipt confirmation (Warehouse status not ready, invalid status, receive
 * result validation failure, or integration consistency mismatch).
 */
public final class MaterialReceiptConfirmationException extends RuntimeException {

    public MaterialReceiptConfirmationException(String message) {
        super(message);
    }

    public MaterialReceiptConfirmationException(String message, Throwable cause) {
        super(message, cause);
    }
}
