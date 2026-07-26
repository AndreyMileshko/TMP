package com.tmp.order.application.document;

import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.OrderDocumentPayload;
import com.tmp.order.domain.PayloadSchemaVersion;
import java.util.Objects;

/**
 * Immutable descriptor of one Order Management business document type (Specification §13.2).
 */
public final class OrderDocumentTypeDescriptor {

    private final DocumentTypeCode documentTypeCode;
    private final String displayName;
    private final String description;
    private final Class<? extends OrderDocumentPayload> payloadClass;
    private final PayloadSchemaVersion payloadSchemaVersion;
    private final String requiredCapability;

    public OrderDocumentTypeDescriptor(
            DocumentTypeCode documentTypeCode,
            String displayName,
            String description,
            Class<? extends OrderDocumentPayload> payloadClass,
            PayloadSchemaVersion payloadSchemaVersion,
            String requiredCapability) {
        this.documentTypeCode = Objects.requireNonNull(documentTypeCode, "documentTypeCode");
        this.displayName = requireNonBlank(displayName, "displayName");
        this.description = Objects.requireNonNull(description, "description");
        this.payloadClass = Objects.requireNonNull(payloadClass, "payloadClass");
        this.payloadSchemaVersion =
                Objects.requireNonNull(payloadSchemaVersion, "payloadSchemaVersion");
        this.requiredCapability = requireNonBlank(requiredCapability, "requiredCapability");
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }

    public DocumentTypeCode documentTypeCode() {
        return documentTypeCode;
    }

    /** Document Engine type id — equals {@link DocumentTypeCode#name()}. */
    public String documentTypeId() {
        return documentTypeCode.name();
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public Class<? extends OrderDocumentPayload> payloadClass() {
        return payloadClass;
    }

    public PayloadSchemaVersion payloadSchemaVersion() {
        return payloadSchemaVersion;
    }

    public String requiredCapability() {
        return requiredCapability;
    }
}
