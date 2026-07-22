package com.tmp.capability.api;

import com.tmp.document.api.DocumentProcessor;
import java.util.Objects;

/**
 * Immutable, pure-data descriptor pairing a document type identifier with the
 * {@link DocumentProcessor} that a Capability contributes for it, via Document Engine's
 * public API. This type performs no registration itself: it fails fast at construction
 * if the given processor does not actually handle the declared document type, so that any
 * mismatch is caught before an integration registrar attempts to register it.
 */
public final class DocumentContribution {

    private final String documentTypeId;
    private final String displayName;
    private final String description;
    private final DocumentProcessor processor;

    private DocumentContribution(
            String documentTypeId, String displayName, String description, DocumentProcessor processor) {
        this.documentTypeId = documentTypeId;
        this.displayName = displayName;
        this.description = description;
        this.processor = processor;
    }

    public static DocumentContribution of(
            String documentTypeId, String displayName, String description, DocumentProcessor processor) {
        requireNonBlank(documentTypeId, "documentTypeId");
        requireNonBlank(displayName, "displayName");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(processor, "processor");
        if (!documentTypeId.equals(processor.documentTypeId())) {
            throw new IllegalArgumentException(
                    "processor.documentTypeId() '" + processor.documentTypeId()
                            + "' does not match declared documentTypeId '" + documentTypeId + "'");
        }
        return new DocumentContribution(documentTypeId, displayName, description, processor);
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    public String documentTypeId() {
        return documentTypeId;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public DocumentProcessor processor() {
        return processor;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DocumentContribution that)) {
            return false;
        }
        return documentTypeId.equals(that.documentTypeId);
    }

    @Override
    public int hashCode() {
        return documentTypeId.hashCode();
    }

    @Override
    public String toString() {
        return documentTypeId;
    }
}
