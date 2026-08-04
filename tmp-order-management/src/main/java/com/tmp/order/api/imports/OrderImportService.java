package com.tmp.order.api.imports;

/**
 * Public application API for source-neutral order import (ADR-029 / ADR-031 / Specification §27.6).
 *
 * <p>Preview performs validation and read-only {@code orderNumber} uniqueness checks without
 * persistence. Confirm atomically creates Order / Items / Revisions / Specification through
 * business documents and lands them as uniform {@code ACTIVE}.
 */
public interface OrderImportService {

    /**
     * Validates the batch and builds a preview. Does not create documents.
     *
     * @throws OrderImportConflictException when orderNumber already exists
     */
    OrderImportPreview preview(OrderImportBatch batch);

    /**
     * Confirms a prepared plan in one transaction. Re-checks validation and order-number
     * uniqueness before creating business documents and activating aggregates.
     *
     * @throws OrderImportValidationException when the plan has validation errors
     * @throws OrderImportConflictException when orderNumber already exists
     * @throws OrderImportProcessingException on unexpected failure (safe message)
     */
    OrderImportConfirmResult confirm(PreparedOrderImportPlan plan);
}
