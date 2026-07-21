package com.tmp.document.support;

import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentProcessor;

public final class TestDocumentProcessor implements DocumentProcessor {

    private final String typeId;

    public TestDocumentProcessor(String typeId) {
        this.typeId = typeId;
    }

    @Override
    public String documentTypeId() {
        return typeId;
    }

    @Override
    public void validateCreate(DocumentOperationContext context) {
        // no-op for infrastructure tests
    }

    @Override
    public void validateUpdate(DocumentOperationContext context) {
        // no-op
    }

    @Override
    public void onPost(DocumentOperationContext context) {
        // no-op
    }

    @Override
    public void onUnpost(DocumentOperationContext context) {
        // no-op
    }

    @Override
    public void onClose(DocumentOperationContext context) {
        // no-op
    }

    @Override
    public void onDelete(DocumentOperationContext context) {
        // no-op
    }
}
