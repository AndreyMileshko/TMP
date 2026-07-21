package com.tmp.document.api;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Optional;

@SuppressFBWarnings(
        value = "CT_CONSTRUCTOR_THROW",
        justification = "Query objects validate pagination parameters at construction time.")
public record DocumentQuery(
        Optional<String> documentTypeId,
        Optional<DocumentStatus> status,
        Optional<String> numberContains,
        int limit,
        int offset) {

    public DocumentQuery {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        documentTypeId = documentTypeId == null ? Optional.empty() : documentTypeId;
        status = status == null ? Optional.empty() : status;
        numberContains = numberContains == null ? Optional.empty() : numberContains;
    }

    public static DocumentQuery all(int limit) {
        return new DocumentQuery(Optional.empty(), Optional.empty(), Optional.empty(), limit, 0);
    }
}
