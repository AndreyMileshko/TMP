package com.tmp.document.api.port;

import java.time.Instant;
import java.util.UUID;

public record DocumentVersionSnapshot(
        UUID id,
        UUID documentId,
        long versionNumber,
        String title,
        Instant changedAt) {
}
