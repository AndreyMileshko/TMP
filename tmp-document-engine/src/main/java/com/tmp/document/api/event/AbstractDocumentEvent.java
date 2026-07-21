package com.tmp.document.api.event;

import java.util.UUID;

public abstract class AbstractDocumentEvent extends com.tmp.core.api.event.AbstractDomainEvent {

    private final UUID documentId;
    private final String documentTypeId;

    protected AbstractDocumentEvent(UUID documentId, String documentTypeId) {
        super("document-engine");
        this.documentId = documentId;
        this.documentTypeId = documentTypeId;
    }

    public UUID documentId() {
        return documentId;
    }

    public String documentTypeId() {
        return documentTypeId;
    }
}
