package com.tmp.security.api;

/**
 * Thrown when the current session lacks a required permission (or has no session).
 */
public final class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String message) {
        super(message);
    }
}
