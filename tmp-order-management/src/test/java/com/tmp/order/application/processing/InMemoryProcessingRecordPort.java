package com.tmp.order.application.processing;

import com.tmp.order.application.payload.DocumentId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link ProcessingRecordPort} for unit tests (STAGE5-018). */
public final class InMemoryProcessingRecordPort implements ProcessingRecordPort {

    private final Map<Key, ProcessingRecord> store = new ConcurrentHashMap<>();

    @Override
    public Optional<ProcessingRecord> findByDocumentIdAndOperation(
            DocumentId documentId, ProcessingOperation operation) {
        return Optional.ofNullable(store.get(new Key(documentId, operation)));
    }

    @Override
    public void insert(ProcessingRecord record) {
        Objects.requireNonNull(record, "record");
        Key key = new Key(record.documentId(), record.operation());
        ProcessingRecord previous = store.putIfAbsent(key, record);
        if (previous != null) {
            throw new DuplicateProcessingRecordException(record.documentId(), record.operation());
        }
    }

    @Override
    public boolean exists(DocumentId documentId, ProcessingOperation operation) {
        return store.containsKey(new Key(documentId, operation));
    }

    private record Key(DocumentId documentId, ProcessingOperation operation) {}
}
