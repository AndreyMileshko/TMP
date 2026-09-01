package com.tmp.security.support;

import com.tmp.security.application.SecurityActivationProperties;
import com.tmp.security.domain.ActivationCodeGenerator;
import java.time.Duration;

/** Shared test fixtures for activation-code security tests. */
public final class ActivationTestSupport {

    public static final String KNOWN_ACTIVATION_CODE = "ABCD-EFGH-JKLM";

    private ActivationTestSupport() {
    }

    public static SecurityActivationProperties defaultActivationProperties() {
        SecurityActivationProperties properties = new SecurityActivationProperties();
        properties.setTtl(Duration.ofHours(24));
        return properties;
    }

    public static ActivationCodeGenerator deterministicGenerator() {
        return new ActivationCodeGenerator(new java.security.SecureRandom() {
            private int index;

            @Override
            public void nextBytes(byte[] bytes) {
                for (int i = 0; i < bytes.length; i++) {
                    bytes[i] = (byte) (index++ % 256);
                }
            }

            @Override
            public int nextInt(int bound) {
                return index++ % bound;
            }
        });
    }
}
