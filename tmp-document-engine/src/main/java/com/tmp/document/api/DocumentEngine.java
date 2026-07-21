package com.tmp.document.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain-independent document lifecycle and registration facade.
 */
public interface DocumentEngine {

    void registerProcessor(DocumentProcessor processor);

    DocumentMetadata createDocument(CreateDocumentCommand command);

    DocumentMetadata updateDocument(UpdateDocumentCommand command);

    DocumentMetadata postDocument(UUID documentId);

    DocumentMetadata unpostDocument(UUID documentId);

    DocumentMetadata closeDocument(UUID documentId);

    void deleteDocument(UUID documentId);

    Optional<DocumentMetadata> findById(UUID documentId);

    List<DocumentMetadata> search(DocumentQuery query);

    List<DocumentTypeDescriptor> registeredTypes();

    DocumentEngineStatus status();
}
