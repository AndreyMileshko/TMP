package com.tmp.order.domain;

/**
 * Thrown when an optimistic-lock {@code version} conflict is detected on save
 * (Database Specification §7). Automatic overwrite is forbidden.
 */
public final class OptimisticLockConflictException extends RuntimeException {

    public OptimisticLockConflictException(String message) {
        super(message);
    }
}
