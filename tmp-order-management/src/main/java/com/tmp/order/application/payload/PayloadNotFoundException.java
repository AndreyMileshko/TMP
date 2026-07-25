package com.tmp.order.application.payload;

/**
 * Thrown when a typed payload cannot be found by {@link DocumentId}.
 */
public final class PayloadNotFoundException extends RuntimeException {

    public PayloadNotFoundException(DocumentId documentId) {
        super("Typed payload not found for document: " + documentId);
    }
}
