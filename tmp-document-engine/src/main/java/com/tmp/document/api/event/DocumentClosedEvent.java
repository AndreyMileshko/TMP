package com.tmp.document.api.event;

import java.util.UUID;

public final class DocumentClosedEvent extends AbstractDocumentEvent {

    public DocumentClosedEvent(UUID documentId, String documentTypeId) {
        super(documentId, documentTypeId);
    }
}
