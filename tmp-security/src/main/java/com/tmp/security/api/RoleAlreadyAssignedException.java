package com.tmp.security.api;

/**
 * Thrown when attempting to assign a role that the user already has.
 */
public final class RoleAlreadyAssignedException extends RuntimeException {

    public RoleAlreadyAssignedException(String message) {
        super(message);
    }
}
