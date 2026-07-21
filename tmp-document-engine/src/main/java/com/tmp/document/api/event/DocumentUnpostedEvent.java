package com.tmp.document.api.event;

import java.util.UUID;

public final class DocumentUnpostedEvent extends AbstractDocumentEvent {

    public DocumentUnpostedEvent(UUID documentId, String documentTypeId) {
        super(documentId, documentTypeId);
    }
}
