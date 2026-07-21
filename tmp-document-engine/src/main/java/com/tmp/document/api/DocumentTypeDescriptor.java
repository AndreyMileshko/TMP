package com.tmp.document.api;

/**
 * Registered document type metadata exposed by Document Engine.
 */
public record DocumentTypeDescriptor(String typeId, String displayName, String description) {
}
