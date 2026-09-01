package com.tmp.security.api;

import java.util.Objects;

/**
 * Thrown when an active user must complete self-service password setup before authentication.
 * Does not reveal whether the login exists to callers other than the matched account holder flow.
 */
public final class PasswordSetupRequiredException extends RuntimeException {

    private final Login login;

    public PasswordSetupRequiredException(Login login) {
        super("Требуется создание пароля");
        this.login = Objects.requireNonNull(login, "login");
    }

    public Login login() {
        return login;
    }
}
