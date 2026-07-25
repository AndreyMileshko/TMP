package com.tmp.order.application.payload;

import com.tmp.order.domain.PayloadRevision;
import java.util.Optional;

/**
 * Persistence port for capability-owned typed document payloads (Specification §11.5 / §19).
 *
 * <p>This is the <strong>single</strong> Order Management payload storage contract (STAGE5-014
 * introduced it for Draft use cases; STAGE5-015 freezes the port surface). Keyed by platform
 * {@link DocumentId}. Stores concrete {@link OrderDocumentPayload} values (document type code and
 * {@link com.tmp.order.domain.PayloadSchemaVersion} are part of {@link PayloadIdentity}). Optimistic
 * locking is expressed via expected {@link PayloadRevision} and {@link
 * PayloadOptimisticLockException}. Absence is {@link Optional#empty()} / {@link
 * PayloadNotFoundException}. {@link #deleteDraft(DocumentId)} removes Draft payload rows; callers
 * (application use cases) must ensure the platform document is still Draft — the port does not
 * depend on Document Engine, JDBC, SQL, JPA or Spring Data. JDBC adapters are out of scope until
 * STAGE5-020.
 */
public interface OrderDocumentPayloadPort {

    Optional<OrderDocumentPayload> findByDocumentId(DocumentId documentId);

    /**
     * Inserts a new typed payload. Implementations must reject duplicate {@link DocumentId}.
     */
    void create(OrderDocumentPayload payload);

    /**
     * Replaces the stored payload when the stored revision equals {@code expectedRevision}.
     *
     * @param payload next immutable payload state (must carry {@code expectedRevision.next()})
     * @param expectedRevision optimistic-lock revision expected in storage
     * @return the stored payload after a successful write
     * @throws PayloadOptimisticLockException when the stored revision does not match
     * @throws PayloadNotFoundException when no payload exists for the document id
     */
    OrderDocumentPayload update(OrderDocumentPayload payload, PayloadRevision expectedRevision);

    /**
     * Deletes a Draft payload by document id. Callers must ensure the platform document is still
     * Draft; the port itself does not talk to Document Engine.
     *
     * @throws PayloadNotFoundException when no payload exists
     */
    void deleteDraft(DocumentId documentId);

    boolean existsByDocumentId(DocumentId documentId);
}
