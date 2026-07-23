package com.tmp.security.api;

/**
 * Thrown when the current password does not match during a self-service password change.
 * Message never contains password or hash material.
 */
public final class InvalidCurrentPasswordException extends RuntimeException {

    public InvalidCurrentPasswordException() {
        super("Current password is incorrect");
    }
}
