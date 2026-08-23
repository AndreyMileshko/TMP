package com.tmp.production.domain;

import java.util.UUID;

/**
 * Raised when a POSTED Production Release payload is mutated.
 */
public final class ProductionReleaseImmutableException extends RuntimeException {

    public ProductionReleaseImmutableException(UUID documentId) {
        super("Posted Production Release is immutable: documentId=" + documentId);
    }
}
