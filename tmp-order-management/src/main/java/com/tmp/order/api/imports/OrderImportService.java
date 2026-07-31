package com.tmp.order.api.imports;

/**
 * Public application API for source-neutral order import (ADR-029 / Specification §27.6).
 *
 * <p>Preview performs validation and read-only conflict/duplicate checks without persistence.
 * Confirm atomically creates DRAFT Order / Items / Revisions / Specification lines through
 * business documents and records import metadata.
 */
public interface OrderImportService {

    /**
     * Validates the batch and builds a preview. Does not create documents or write metadata.
     *
     * @throws OrderImportDuplicateException when checksum was already imported for the source type
     * @throws OrderImportConflictException when orderNumber already exists
     */
    OrderImportPreview preview(OrderImportBatch batch);

    /**
     * Confirms a prepared plan in one transaction. Re-checks validation, duplicate protection and
     * order-number conflict before creating business documents.
     *
     * @throws OrderImportValidationException when the plan has validation errors
     * @throws OrderImportDuplicateException when checksum was already imported
     * @throws OrderImportConflictException when orderNumber already exists
     * @throws OrderImportProcessingException on unexpected failure (safe message)
     */
    OrderImportConfirmResult confirm(PreparedOrderImportPlan plan);
}
