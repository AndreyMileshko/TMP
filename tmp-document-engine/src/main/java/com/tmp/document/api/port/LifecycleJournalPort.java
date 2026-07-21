package com.tmp.document.api.port;

import com.tmp.document.api.DocumentStatus;
import com.tmp.document.api.LifecycleEventType;
import com.tmp.document.api.LifecycleJournalEntry;
import java.util.List;
import java.util.UUID;

public interface LifecycleJournalPort {

    LifecycleJournalEntry append(
            UUID documentId,
            LifecycleEventType eventType,
            DocumentStatus fromStatus,
            DocumentStatus toStatus,
            String details);

    List<LifecycleJournalEntry> findByDocumentId(UUID documentId);
}
