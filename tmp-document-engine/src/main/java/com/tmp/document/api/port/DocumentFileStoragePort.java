package com.tmp.document.api.port;

import java.util.List;
import java.util.UUID;

public interface DocumentFileStoragePort {

    DocumentFileReference registerReference(DocumentFileReference reference);

    List<DocumentFileReference> findByDocumentId(UUID documentId);
}
