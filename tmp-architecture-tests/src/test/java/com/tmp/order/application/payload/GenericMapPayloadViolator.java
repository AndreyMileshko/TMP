package com.tmp.order.application.payload;

import java.util.Map;

/**
 * Test-only architecture violator for Stage 5.11A negative verification.
 *
 * <p>Intentionally uses {@code Map<String, Object>} in a typed payload package. Never used in
 * production.
 */
public final class GenericMapPayloadViolator {

    private final Map<String, Object> payload;

    public GenericMapPayloadViolator(Map<String, Object> payload) {
        this.payload = payload;
    }

    public Map<String, Object> payload() {
        return payload;
    }
}
