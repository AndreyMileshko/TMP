package com.tmp.security.api;

/**
 * Authentication failed with a generic message that does not reveal user existence.
 */
public final class AuthenticationFailedException extends RuntimeException {

    public static final String GENERIC_MESSAGE = "Неверный логин или пароль";

    public AuthenticationFailedException() {
        super(GENERIC_MESSAGE);
    }
}
