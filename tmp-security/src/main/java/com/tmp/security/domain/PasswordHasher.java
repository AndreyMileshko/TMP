package com.tmp.security.domain;

/**
 * Domain port for password hashing and verification.
 *
 * <p>Plaintext is accepted as {@code char[]} so callers can zero the array after use.
 * Implementations must not retain the plaintext array after the call returns.
 */
public interface PasswordHasher {

    /**
     * Hashes the given plaintext password. Does not retain the {@code char[]} contents.
     */
    PasswordHash hash(char[] plaintextPassword);

    /**
     * Verifies plaintext against an encoded hash. Does not retain the {@code char[]} contents.
     */
    boolean matches(char[] plaintextPassword, PasswordHash hash);
}
