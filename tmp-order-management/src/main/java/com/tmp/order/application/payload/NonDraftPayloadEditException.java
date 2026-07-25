package com.tmp.order.application.payload;

import com.tmp.document.api.DocumentStatus;

/**
 * Thrown when a typed payload create/update/delete is attempted while the platform document is not
 * in editable {@link DocumentStatus#DRAFT} (Specification §11.3).
 */
public final class NonDraftPayloadEditException extends RuntimeException {

    private final DocumentId documentId;
    private final DocumentStatus status;

    public NonDraftPayloadEditException(DocumentId documentId, DocumentStatus status) {
        super(
                "Typed payload of document "
                        + documentId
                        + " is not editable; platform status is "
                        + status);
        this.documentId = documentId;
        this.status = status;
    }

    public NonDraftPayloadEditException(DocumentId documentId, String detail) {
        super("Typed payload of document " + documentId + " is not editable: " + detail);
        this.documentId = documentId;
        this.status = null;
    }

    public DocumentId documentId() {
        return documentId;
    }

    public DocumentStatus status() {
        return status;
    }
}
