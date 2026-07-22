package com.tmp.capability.sample;

import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentProcessor;

/**
 * Minimal no-op {@link DocumentProcessor} used as a technical fixture by
 * {@link SampleTechnicalCapability}. It performs no business validation or side effects —
 * all lifecycle hooks are intentionally empty.
 */
public final class SampleTechnicalDocumentProcessor implements DocumentProcessor {

    /** Document type id contributed by the sample technical capability. */
    public static final String DOCUMENT_TYPE_ID = "sample.technical.document";

    @Override
    public String documentTypeId() {
        return DOCUMENT_TYPE_ID;
    }

    @Override
    public void validateCreate(DocumentOperationContext context) {
        // technical fixture: no business rules
    }

    @Override
    public void validateUpdate(DocumentOperationContext context) {
        // technical fixture: no business rules
    }

    @Override
    public void onPost(DocumentOperationContext context) {
        // technical fixture: no business rules
    }

    @Override
    public void onUnpost(DocumentOperationContext context) {
        // technical fixture: no business rules
    }

    @Override
    public void onClose(DocumentOperationContext context) {
        // technical fixture: no business rules
    }

    @Override
    public void onDelete(DocumentOperationContext context) {
        // technical fixture: no business rules
    }
}
