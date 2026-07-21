package com.tmp.document.api;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.UUID;

@SuppressFBWarnings(
        value = "CT_CONSTRUCTOR_THROW",
        justification = "Command objects validate required fields at construction time.")
public record UpdateDocumentCommand(UUID documentId, String title, long expectedVersion) {

    public UpdateDocumentCommand {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
    }
}
