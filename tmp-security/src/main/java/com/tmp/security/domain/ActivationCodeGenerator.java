package com.tmp.security.domain;

import java.security.SecureRandom;
import java.util.Objects;

/**
 * Cryptographically secure activation code generator. Plaintext codes exist only at generation time.
 */
public final class ActivationCodeGenerator {

    /** Excludes visually ambiguous O/0 and I/1. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int GROUP_LENGTH = 4;
    private static final int GROUP_COUNT = 3;

    private final SecureRandom random;

    public ActivationCodeGenerator() {
        this(new SecureRandom());
    }

    public ActivationCodeGenerator(SecureRandom random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * Generates a human-readable code in {@code XXXX-XXXX-XXXX} format (48 bits of entropy).
     */
    public String generate() {
        StringBuilder builder = new StringBuilder();
        for (int group = 0; group < GROUP_COUNT; group++) {
            if (group > 0) {
                builder.append('-');
            }
            for (int i = 0; i < GROUP_LENGTH; i++) {
                builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
        }
        return builder.toString();
    }

    /** Normalizes user input for hashing and comparison (strips dashes, uppercases). */
    public static String normalize(String rawCode) {
        Objects.requireNonNull(rawCode, "rawCode");
        return rawCode.replace("-", "").trim().toUpperCase();
    }
}
