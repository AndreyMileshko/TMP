package com.tmp.order.application.payload;

import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentStatus;
import com.tmp.order.domain.PayloadRevision;
import java.util.Objects;
import java.util.Optional;

/**
 * Internal application use cases for Draft typed payload lifecycle (Specification §11.3 / §11.4).
 *
 * <p>Not part of the Order Management public API. Platform document Draft state is verified through
 * the public {@link DocumentEngine} contract ({@link DocumentStatus#DRAFT}). Optimistic locking uses
 * {@link PayloadRevision}.
 */
public final class DraftPayloadApplicationService {

    private final DocumentEngine documentEngine;
    private final OrderDocumentPayloadPort payloadPort;

    public DraftPayloadApplicationService(
            DocumentEngine documentEngine, OrderDocumentPayloadPort payloadPort) {
        this.documentEngine = Objects.requireNonNull(documentEngine, "documentEngine");
        this.payloadPort = Objects.requireNonNull(payloadPort, "payloadPort");
    }

    /**
     * Stores a new Draft typed payload after verifying the platform document is Draft.
     */
    public OrderDocumentPayload createDraft(OrderDocumentPayload payload) {
        Objects.requireNonNull(payload, "payload");
        requireEditableDraft(payload.documentId());
        if (payloadPort.existsByDocumentId(payload.documentId())) {
            throw new PayloadAlreadyExistsException(payload.documentId());
        }
        if (!payload.identity().payloadRevision().equals(PayloadRevision.initial())) {
            throw new IllegalArgumentException(
                    "New draft payload must start at revision 0, got "
                            + payload.identity().payloadRevision());
        }
        payloadPort.create(payload);
        return payload;
    }

    /**
     * Loads a typed payload by {@link DocumentId}. Does not require Draft — historical payloads of
     * posted documents remain readable for processors; mutation paths enforce Draft separately.
     */
    public Optional<OrderDocumentPayload> load(DocumentId documentId) {
        Objects.requireNonNull(documentId, "documentId");
        return payloadPort.findByDocumentId(documentId);
    }

    /**
     * Loads a Draft payload and rejects when the platform document is no longer Draft.
     */
    public OrderDocumentPayload loadDraft(DocumentId documentId) {
        requireEditableDraft(documentId);
        return payloadPort
                .findByDocumentId(documentId)
                .orElseThrow(() -> new PayloadNotFoundException(documentId));
    }

    /**
     * Updates a Draft payload with optimistic locking.
     *
     * <p>{@code candidate} must already carry {@code expectedRevision.next()} (as produced by typed
     * {@code with*} methods). Expected and stored revisions are compared; on success the stored
     * revision becomes the candidate's revision.
     */
    public OrderDocumentPayload updateDraft(
            OrderDocumentPayload candidate, PayloadRevision expectedRevision) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(expectedRevision, "expectedRevision");
        requireEditableDraft(candidate.documentId());

        OrderDocumentPayload current =
                payloadPort
                        .findByDocumentId(candidate.documentId())
                        .orElseThrow(() -> new PayloadNotFoundException(candidate.documentId()));

        PayloadRevision actual = current.identity().payloadRevision();
        if (!actual.equals(expectedRevision)) {
            throw new PayloadOptimisticLockException(
                    candidate.documentId(), expectedRevision, actual);
        }

        PayloadRevision expectedNext = expectedRevision.next();
        if (!candidate.identity().payloadRevision().equals(expectedNext)) {
            throw new IllegalArgumentException(
                    "Updated payload must carry next PayloadRevision "
                            + expectedNext
                            + ", got "
                            + candidate.identity().payloadRevision());
        }

        if (candidate.documentTypeCode() != current.documentTypeCode()) {
            throw new IllegalArgumentException(
                    "Payload document type cannot change: "
                            + current.documentTypeCode()
                            + " -> "
                            + candidate.documentTypeCode());
        }

        return payloadPort.update(candidate, expectedRevision);
    }

    /**
     * Deletes a Draft typed payload. Platform document must still be Draft (Specification §14.3).
     */
    public void deleteDraft(DocumentId documentId) {
        Objects.requireNonNull(documentId, "documentId");
        requireEditableDraft(documentId);
        if (!payloadPort.existsByDocumentId(documentId)) {
            throw new PayloadNotFoundException(documentId);
        }
        payloadPort.deleteDraft(documentId);
    }

    private void requireEditableDraft(DocumentId documentId) {
        DocumentMetadata metadata =
                documentEngine
                        .findById(documentId.value())
                        .orElseThrow(
                                () -> new NonDraftPayloadEditException(
                                        documentId, "platform document not found"));
        if (metadata.status() != DocumentStatus.DRAFT) {
            throw new NonDraftPayloadEditException(documentId, metadata.status());
        }
    }
}
