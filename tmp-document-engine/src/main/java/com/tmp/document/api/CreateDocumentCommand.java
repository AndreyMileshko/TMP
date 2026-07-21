package com.tmp.document.api;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.UUID;

@SuppressFBWarnings(
        value = "CT_CONSTRUCTOR_THROW",
        justification = "Command objects validate required fields at construction time.")
public record CreateDocumentCommand(String documentTypeId, String title) {

    public CreateDocumentCommand {
        if (documentTypeId == null || documentTypeId.isBlank()) {
            throw new IllegalArgumentException("documentTypeId must not be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
    }
}
