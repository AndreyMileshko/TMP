package com.tmp.document.api.event;

import java.util.UUID;

public final class DocumentDeletedEvent extends AbstractDocumentEvent {

    public DocumentDeletedEvent(UUID documentId, String documentTypeId) {
        super(documentId, documentTypeId);
    }
}
