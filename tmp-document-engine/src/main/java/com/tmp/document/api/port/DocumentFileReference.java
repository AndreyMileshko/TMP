package com.tmp.document.api.port;

import java.time.Instant;
import java.util.UUID;

public record DocumentFileReference(
        UUID id,
        UUID documentId,
        String path,
        String mimeType,
        long sizeBytes,
        String checksum,
        Instant createdAt) {
}
