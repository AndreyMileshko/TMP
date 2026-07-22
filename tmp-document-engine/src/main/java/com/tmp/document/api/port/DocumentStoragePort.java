package com.tmp.document.api.port;

import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentQuery;
import com.tmp.document.api.DocumentTypeDescriptor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentStoragePort {

    DocumentMetadata insert(DocumentMetadata document);

    DocumentMetadata update(DocumentMetadata document, long expectedVersion);

    void delete(UUID documentId);

    Optional<DocumentMetadata> findById(UUID documentId);

    List<DocumentMetadata> search(DocumentQuery query);

    long countAll();

    List<DocumentTypeDescriptor> listDocumentTypes();

    void registerDocumentType(String typeId, String displayName, String description);

    boolean documentTypeExists(String typeId);

    boolean hasDocumentsForType(String typeId);

    /**
     * Removes the document type row when {@link #hasDocumentsForType(String)} is false.
     */
    void unregisterDocumentType(String typeId);
}
