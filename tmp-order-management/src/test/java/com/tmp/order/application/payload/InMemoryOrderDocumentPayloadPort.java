package com.tmp.order.application.payload;

import com.tmp.order.domain.PayloadRevision;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link OrderDocumentPayloadPort} for unit tests (STAGE5-014). Not a production adapter.
 */
public final class InMemoryOrderDocumentPayloadPort implements OrderDocumentPayloadPort {

    private final Map<DocumentId, OrderDocumentPayload> store = new ConcurrentHashMap<>();

    @Override
    public Optional<OrderDocumentPayload> findByDocumentId(DocumentId documentId) {
        Objects.requireNonNull(documentId, "documentId");
        return Optional.ofNullable(store.get(documentId));
    }

    @Override
    public void create(OrderDocumentPayload payload) {
        Objects.requireNonNull(payload, "payload");
        OrderDocumentPayload previous = store.putIfAbsent(payload.documentId(), payload);
        if (previous != null) {
            throw new PayloadAlreadyExistsException(payload.documentId());
        }
    }

    @Override
    public OrderDocumentPayload update(
            OrderDocumentPayload payload, PayloadRevision expectedRevision) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(expectedRevision, "expectedRevision");
        OrderDocumentPayload current = store.get(payload.documentId());
        if (current == null) {
            throw new PayloadNotFoundException(payload.documentId());
        }
        if (!current.identity().payloadRevision().equals(expectedRevision)) {
            throw new PayloadOptimisticLockException(
                    payload.documentId(),
                    expectedRevision,
                    current.identity().payloadRevision());
        }
        store.put(payload.documentId(), payload);
        return payload;
    }

    @Override
    public void deleteDraft(DocumentId documentId) {
        Objects.requireNonNull(documentId, "documentId");
        if (store.remove(documentId) == null) {
            throw new PayloadNotFoundException(documentId);
        }
    }

    @Override
    public boolean existsByDocumentId(DocumentId documentId) {
        Objects.requireNonNull(documentId, "documentId");
        return store.containsKey(documentId);
    }
}
