package com.tmp.production.domain;

import java.util.UUID;

/** Raised when a posted Production Cancellation payload is mutated. */
public final class ProductionCancellationImmutableException extends RuntimeException {

    private final UUID documentId;

    public ProductionCancellationImmutableException(UUID documentId) {
        super("Production Cancellation is immutable after POST: " + documentId);
        this.documentId = documentId;
    }

    public UUID documentId() {
        return documentId;
    }
}
