package com.tmp.security.api;

/**
 * Result of an administrative password reset including a one-time activation code.
 */
public record PasswordResetResult(UserSummary user, String activationCode) {
}
