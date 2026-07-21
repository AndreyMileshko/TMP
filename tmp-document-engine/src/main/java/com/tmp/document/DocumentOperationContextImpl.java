package com.tmp.document;

import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentOperationContext;

final class DocumentOperationContextImpl implements DocumentOperationContext {

    private final DocumentMetadata document;

    DocumentOperationContextImpl(DocumentMetadata document) {
        this.document = document;
    }

    @Override
    public DocumentMetadata document() {
        return document;
    }
}
