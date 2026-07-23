package com.tmp.security.domain;

/**
 * Raised when two Capabilities claim the same {@link com.tmp.security.api.PermissionId}.
 */
public final class PermissionOwnershipConflictException extends RuntimeException {

    public PermissionOwnershipConflictException(String message) {
        super(message);
    }
}
