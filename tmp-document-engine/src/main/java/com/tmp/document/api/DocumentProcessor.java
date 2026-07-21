package com.tmp.document.api;

/**
 * Business logic handler for a single document type. One processor per typeId.
 */
public interface DocumentProcessor {

    String documentTypeId();

    void validateCreate(DocumentOperationContext context);

    void validateUpdate(DocumentOperationContext context);

    void onPost(DocumentOperationContext context);

    void onUnpost(DocumentOperationContext context);

    void onClose(DocumentOperationContext context);

    void onDelete(DocumentOperationContext context);
}
