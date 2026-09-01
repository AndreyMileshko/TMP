package com.tmp.security.api;

/**
 * Thrown when password confirmation does not match the new password.
 */
public final class PasswordConfirmationMismatchException extends RuntimeException {

    public PasswordConfirmationMismatchException() {
        super("Пароли не совпадают");
    }
}
