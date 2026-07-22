package com.tmp.document;

import com.tmp.document.api.DocumentProcessor;
import com.tmp.document.api.DocumentTypeDescriptor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DefaultDocumentProcessorRegistry {

    private final Map<String, DocumentProcessor> processors = new LinkedHashMap<>();

    public synchronized void register(DocumentProcessor processor) {
        String typeId = processor.documentTypeId();
        if (typeId == null || typeId.isBlank()) {
            throw new IllegalArgumentException("documentTypeId must not be blank");
        }
        if (processors.containsKey(typeId)) {
            throw new IllegalStateException("Document processor already registered for type: " + typeId);
        }
        processors.put(typeId, processor);
    }

    /**
     * Removes a processor registration. Idempotent — used for transaction rollback compensation.
     */
    public synchronized void unregister(String documentTypeId) {
        if (documentTypeId != null) {
            processors.remove(documentTypeId);
        }
    }

    public synchronized DocumentProcessor require(String documentTypeId) {
        DocumentProcessor processor = processors.get(documentTypeId);
        if (processor == null) {
            throw new IllegalStateException("No document processor registered for type: " + documentTypeId);
        }
        return processor;
    }

    public synchronized boolean isRegistered(String documentTypeId) {
        return processors.containsKey(documentTypeId);
    }

    public synchronized List<DocumentTypeDescriptor> registeredTypes() {
        return processors.values().stream()
                .map(processor -> new DocumentTypeDescriptor(
                        processor.documentTypeId(),
                        processor.documentTypeId(),
                        "Registered document processor"))
                .toList();
    }

    public synchronized int size() {
        return processors.size();
    }
}
