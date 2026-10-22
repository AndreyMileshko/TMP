package com.tmp.security.api;

/**
 * Thrown when a role revocation would leave the system without any ACTIVE user who can
 * administer role assignments ({@link SecurityPermissions#ROLES_ASSIGN}).
 */
public final class LastAdministratorException extends RuntimeException {

    public LastAdministratorException(String message) {
        super(message);
    }
}