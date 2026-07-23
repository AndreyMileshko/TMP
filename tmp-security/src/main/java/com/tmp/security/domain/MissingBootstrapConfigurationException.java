package com.tmp.security.domain;

/**
 * Thrown when bootstrap administrator credentials are required but missing or blank.
 */
public final class MissingBootstrapConfigurationException extends RuntimeException {

    public MissingBootstrapConfigurationException(String message) {
        super(message);
    }
}
