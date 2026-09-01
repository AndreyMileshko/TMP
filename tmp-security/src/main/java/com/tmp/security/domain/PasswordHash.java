package com.tmp.security.domain;

import java.util.Objects;

/**
 * Immutable carrier of an already-encoded password hash. Never holds plaintext.
 *
 * <p>{@link #encodedValue()} is infrastructure-only (persistence / hasher adapters).
 * Never log, audit, or serialize the encoded value.
 */
public final class PasswordHash {

    private static final String REDACTED = "PasswordHash[REDACTED]";

    /**
     * Sentinel stored for accounts awaiting self-service password setup. Valid BCrypt format but not
     * derived from any known plaintext; {@link com.tmp.security.domain.PasswordHasher#matches} must
     * never succeed for real passwords against this value.
     */
    private static final PasswordHash UNINITIALIZED = new PasswordHash(
            "$2a$10$XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");

    private final String encodedHash;

    private PasswordHash(String encodedHash) {
        this.encodedHash = encodedHash;
    }

    /**
     * Creates a hash carrier from an already-encoded hash string.
     * Used by infrastructure adapters only — never pass plaintext.
     */
    public static PasswordHash of(String encodedHash) {
        Objects.requireNonNull(encodedHash, "encodedHash");
        if (encodedHash.isBlank()) {
            throw new IllegalArgumentException("Password hash must not be blank");
        }
        return new PasswordHash(encodedHash);
    }

    /** Placeholder hash for accounts that have not yet completed self-service password setup. */
    public static PasswordHash uninitialized() {
        return UNINITIALIZED;
    }

    public boolean isUninitialized() {
        return this == UNINITIALIZED;
    }

    /**
     * Infrastructure-only accessor for the encoded hash. Never log, audit, or serialize.
     */
    public String encodedValue() {
        return encodedHash;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PasswordHash that)) {
            return false;
        }
        return encodedHash.equals(that.encodedHash);
    }

    @Override
    public int hashCode() {
        return encodedHash.hashCode();
    }

    @Override
    public String toString() {
        return REDACTED;
    }
}
