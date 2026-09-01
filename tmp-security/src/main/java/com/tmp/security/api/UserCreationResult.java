package com.tmp.security.api;

/**
 * Result of user creation including a one-time activation code for the administrator.
 */
public record UserCreationResult(UserSummary user, String activationCode) {
}
