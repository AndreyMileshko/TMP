package com.tmp.document.api.event;

import java.util.UUID;

public final class DocumentCreatedEvent extends AbstractDocumentEvent {

    public DocumentCreatedEvent(UUID documentId, String documentTypeId) {
        super(documentId, documentTypeId);
    }
}
