package com.tmp.production.persistence;

/** Persistence-layer failure surfaced to application adapters. */
public final class ProductionPersistenceException extends RuntimeException {

    public ProductionPersistenceException(String message) {
        super(message);
    }

    public ProductionPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
