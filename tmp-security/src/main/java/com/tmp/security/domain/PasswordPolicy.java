package com.tmp.security.domain;

import java.util.Arrays;
import java.util.Objects;

/**
 * Validates password input for self-service setup and change flows.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    private PasswordPolicy() {
    }

    public static void validate(char[] password, char[] confirmation) {
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(confirmation, "confirmation");
        validateNewPassword(password);
        if (!Arrays.equals(password, confirmation)) {
            throw new com.tmp.security.api.PasswordConfirmationMismatchException();
        }
    }

    public static void validateNewPassword(char[] password) {
        Objects.requireNonNull(password, "password");
        if (password.length == 0) {
            throw new com.tmp.security.api.InvalidPasswordException("Пароль не может быть пустым");
        }
        if (password.length < MIN_LENGTH) {
            throw new com.tmp.security.api.InvalidPasswordException(
                    "Пароль должен содержать не менее " + MIN_LENGTH + " символов");
        }
    }
}
