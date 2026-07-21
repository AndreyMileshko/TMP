package com.tmp.document.api.event;

import java.util.UUID;

public final class DocumentPostedEvent extends AbstractDocumentEvent {

    public DocumentPostedEvent(UUID documentId, String documentTypeId) {
        super(documentId, documentTypeId);
    }
}
