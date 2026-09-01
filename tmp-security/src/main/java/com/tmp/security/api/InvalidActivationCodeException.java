package com.tmp.security.api;

/**
 * Thrown when an activation code is missing, invalid, expired, or already consumed.
 * Message is intentionally neutral to avoid account enumeration.
 */
public final class InvalidActivationCodeException extends RuntimeException {

    public static final String NEUTRAL_MESSAGE = "Неверный или недействительный код активации";

    public InvalidActivationCodeException() {
        super(NEUTRAL_MESSAGE);
    }
}
