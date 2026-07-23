package com.tmp.security.api;

/**
 * Thrown when attempting to delete a role that still has assigned users.
 */
public final class RoleInUseException extends RuntimeException {

    public RoleInUseException(String message) {
        super(message);
    }
}
