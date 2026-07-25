package com.tmp.order.application.payload;

import com.tmp.order.domain.PayloadRevision;

/**
 * Thrown when an optimistic-lock conflict is detected on typed payload update (Specification
 * §11.3): expected {@link PayloadRevision} does not match the stored revision.
 */
public final class PayloadOptimisticLockException extends RuntimeException {

    private final DocumentId documentId;
    private final PayloadRevision expected;
    private final PayloadRevision actual;

    public PayloadOptimisticLockException(
            DocumentId documentId, PayloadRevision expected, PayloadRevision actual) {
        super(
                "Payload revision conflict for document "
                        + documentId
                        + ": expected "
                        + expected
                        + ", actual "
                        + actual);
        this.documentId = documentId;
        this.expected = expected;
        this.actual = actual;
    }

    public DocumentId documentId() {
        return documentId;
    }

    public PayloadRevision expected() {
        return expected;
    }

    public PayloadRevision actual() {
        return actual;
    }
}
