package com.tmp.security.infrastructure;

import com.tmp.security.domain.PasswordHash;
import com.tmp.security.domain.PasswordHasher;
import java.util.Objects;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCrypt implementation of {@link PasswordHasher} using spring-security-crypto only.
 */
public final class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder;

    public BCryptPasswordHasher() {
        this(new BCryptPasswordEncoder());
    }

    BCryptPasswordHasher(BCryptPasswordEncoder encoder) {
        this.encoder = Objects.requireNonNull(encoder, "encoder");
    }

    @Override
    public PasswordHash hash(char[] plaintextPassword) {
        Objects.requireNonNull(plaintextPassword, "plaintextPassword");
        if (plaintextPassword.length == 0) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        String encoded = encoder.encode(new String(plaintextPassword));
        return PasswordHash.of(encoded);
    }

    @Override
    public boolean matches(char[] plaintextPassword, PasswordHash hash) {
        Objects.requireNonNull(plaintextPassword, "plaintextPassword");
        Objects.requireNonNull(hash, "hash");
        return encoder.matches(new String(plaintextPassword), hash.encodedValue());
    }
}
