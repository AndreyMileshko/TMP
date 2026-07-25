package com.tmp.order.application.payload;

/**
 * Thrown when creating a typed payload for a {@link DocumentId} that already has one stored.
 */
public final class PayloadAlreadyExistsException extends RuntimeException {

    public PayloadAlreadyExistsException(DocumentId documentId) {
        super("Typed payload already exists for document: " + documentId);
    }
}
