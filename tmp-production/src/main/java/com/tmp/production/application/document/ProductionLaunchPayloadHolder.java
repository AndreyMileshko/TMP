package com.tmp.production.application.document;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory holder for Production Launch payloads keyed by document ID.
 *
 * <p>The application service stores the payload before calling
 * {@code DocumentEngine.postDocument()} so the processor can retrieve it during {@code onPost}.
 * Payloads are removed after use to prevent memory leaks.
 */
public final class ProductionLaunchPayloadHolder {

    private final Map<UUID, ProductionLaunchPayload> payloads = new ConcurrentHashMap<>();

    public void set(UUID documentId, ProductionLaunchPayload payload) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(payload, "payload");
        payloads.put(documentId, payload);
    }

    public ProductionLaunchPayload require(UUID documentId) {
        ProductionLaunchPayload payload = payloads.remove(documentId);
        if (payload == null) {
            throw new IllegalStateException(
                    "No Production Launch payload found for document " + documentId);
        }
        return payload;
    }

    public void clear(UUID documentId) {
        payloads.remove(documentId);
    }
}
