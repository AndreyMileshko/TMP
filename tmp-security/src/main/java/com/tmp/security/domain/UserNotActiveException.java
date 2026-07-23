package com.tmp.security.domain;

/**
 * Thrown when a mutation targets a logically deleted (or missing active) user.
 */
public final class UserNotActiveException extends RuntimeException {

    public UserNotActiveException(String message) {
        super(message);
    }
}
