package com.tmp.order.application.payload;

import com.tmp.order.domain.PayloadRevision;
import com.tmp.order.domain.PayloadSchemaVersion;
import java.time.Instant;
import java.util.Objects;

/**
 * Shared identity and versioning fields of every capability-owned typed document payload
 * (Specification §11.2).
 *
 * <p>Immutable value object. Not a generic JSON envelope — business fields live on the concrete
 * typed payload type.
 */
public final class PayloadIdentity {

    private final DocumentId documentId;
    private final DocumentTypeCode documentTypeCode;
    private final PayloadSchemaVersion schemaVersion;
    private final PayloadRevision payloadRevision;
    private final Instant createdAt;
    private final Instant updatedAt;

    private PayloadIdentity(
            DocumentId documentId,
            DocumentTypeCode documentTypeCode,
            PayloadSchemaVersion schemaVersion,
            PayloadRevision payloadRevision,
            Instant createdAt,
            Instant updatedAt) {
        this.documentId = Objects.requireNonNull(documentId, "documentId");
        this.documentTypeCode = Objects.requireNonNull(documentTypeCode, "documentTypeCode");
        this.schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion");
        this.payloadRevision = Objects.requireNonNull(payloadRevision, "payloadRevision");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static PayloadIdentity of(
            DocumentId documentId,
            DocumentTypeCode documentTypeCode,
            PayloadSchemaVersion schemaVersion,
            PayloadRevision payloadRevision,
            Instant createdAt,
            Instant updatedAt) {
        return new PayloadIdentity(
                documentId, documentTypeCode, schemaVersion, payloadRevision, createdAt, updatedAt);
    }

    /**
     * Creates identity for a newly stored draft payload (schema v1, revision 0).
     */
    public static PayloadIdentity initialDraft(
            DocumentId documentId, DocumentTypeCode documentTypeCode, Instant now) {
        Objects.requireNonNull(now, "now");
        return new PayloadIdentity(
                documentId,
                documentTypeCode,
                PayloadSchemaVersion.initial(),
                PayloadRevision.initial(),
                now,
                now);
    }

    public PayloadIdentity withNextRevision(Instant updatedAt) {
        Objects.requireNonNull(updatedAt, "updatedAt");
        return new PayloadIdentity(
                documentId,
                documentTypeCode,
                schemaVersion,
                payloadRevision.next(),
                createdAt,
                updatedAt);
    }

    public DocumentId documentId() {
        return documentId;
    }

    public DocumentTypeCode documentTypeCode() {
        return documentTypeCode;
    }

    public PayloadSchemaVersion schemaVersion() {
        return schemaVersion;
    }

    public PayloadRevision payloadRevision() {
        return payloadRevision;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PayloadIdentity that)) {
            return false;
        }
        return documentId.equals(that.documentId)
                && documentTypeCode == that.documentTypeCode
                && schemaVersion.equals(that.schemaVersion)
                && payloadRevision.equals(that.payloadRevision)
                && createdAt.equals(that.createdAt)
                && updatedAt.equals(that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                documentId, documentTypeCode, schemaVersion, payloadRevision, createdAt, updatedAt);
    }
}
