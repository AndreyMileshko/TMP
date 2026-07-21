package com.tmp.document.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain-independent document metadata snapshot.
 */
public record DocumentMetadata(
        UUID id,
        String documentTypeId,
        String documentNumber,
        String title,
        DocumentStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant postedAt,
        Instant closedAt) {
}
