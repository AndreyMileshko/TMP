package com.tmp.document.api.port;

import java.util.List;
import java.util.UUID;

public interface DocumentVersionPort {

    void saveSnapshot(DocumentVersionSnapshot snapshot);

    List<DocumentVersionSnapshot> findByDocumentId(UUID documentId);
}
