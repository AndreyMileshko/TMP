package com.tmp.security.api;

/**
 * Thrown when a password does not satisfy policy rules.
 */
public final class InvalidPasswordException extends RuntimeException {

    public InvalidPasswordException(String message) {
        super(message);
    }
}
