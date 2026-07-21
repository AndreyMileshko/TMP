package com.tmp.document.api;

import java.time.Instant;
import java.util.UUID;

public record LifecycleJournalEntry(
        UUID id,
        UUID documentId,
        LifecycleEventType eventType,
        DocumentStatus fromStatus,
        DocumentStatus toStatus,
        Instant occurredAt,
        String details) {
}
