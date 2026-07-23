package com.tmp.security.domain;

/**
 * Thrown when a login collides with an existing user (case-insensitive uniqueness).
 */
public final class DuplicateLoginException extends RuntimeException {

    public DuplicateLoginException(String message) {
        super(message);
    }

    public DuplicateLoginException(String message, Throwable cause) {
        super(message, cause);
    }
}
