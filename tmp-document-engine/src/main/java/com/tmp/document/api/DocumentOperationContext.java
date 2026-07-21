package com.tmp.document.api;

/**
 * Context passed to Document Processor lifecycle hooks.
 */
public interface DocumentOperationContext {

    DocumentMetadata document();
}
