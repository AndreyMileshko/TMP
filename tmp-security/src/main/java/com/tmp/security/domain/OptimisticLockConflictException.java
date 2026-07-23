package com.tmp.security.domain;

/**
 * Thrown when an optimistic-lock version conflict is detected on save.
 */
public final class OptimisticLockConflictException extends RuntimeException {

    public OptimisticLockConflictException(String message) {
        super(message);
    }
}
