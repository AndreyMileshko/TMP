package com.tmp.security.domain;

/**
 * Thrown when a logically deleted user is mutated again (e.g. deleted a second time).
 */
public final class UserAlreadyDeletedException extends RuntimeException {

    public UserAlreadyDeletedException(String message) {
        super(message);
    }
}
