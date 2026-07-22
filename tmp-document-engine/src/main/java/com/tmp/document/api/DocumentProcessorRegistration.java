package com.tmp.document.api;

/**
 * Reversible handle for a document processor contribution. {@link #unregister()} fully
 * removes the registration (used for registration rollback). {@link #deactivate()} disables
 * new document operations for the type while preserving existing persisted documents.
 */
public interface DocumentProcessorRegistration {

    String documentTypeId();

    /**
     * Removes the processor registration. When no documents exist for the type, the
     * document-type metadata row is removed as well.
     */
    void unregister();

    /**
     * Disables new document operations for this type. Existing documents remain queryable
     * and mutable according to their current lifecycle state.
     */
    void deactivate();
}
