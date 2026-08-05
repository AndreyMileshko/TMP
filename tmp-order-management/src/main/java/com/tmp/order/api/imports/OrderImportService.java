package com.tmp.order.api.imports;

import java.util.List;

/**
 * Public application API for source-neutral order import (ADR-029 / ADR-031 / Final STXT Contract).
 *
 * <p>Preview performs validation and read-only {@code orderNumber} uniqueness checks without
 * persistence. Confirm atomically creates Order / Items / Revisions / Specification through
 * business documents and lands them as uniform {@code ACTIVE}. One file may import several orders.
 */
public interface OrderImportService {

    /**
     * Validates a single-order batch and builds a preview. Does not create documents.
     *
     * @throws OrderImportConflictException when orderNumber already exists
     */
    OrderImportPreview preview(OrderImportBatch batch);

    /**
     * Validates one or more orders from a file and builds a combined preview.
     *
     * @throws OrderImportConflictException when any orderNumber already exists
     */
    OrderImportPreview preview(List<OrderImportBatch> batches);

    /**
     * Confirms a prepared plan in one transaction. Re-checks validation and order-number
     * uniqueness before creating business documents and activating aggregates. Creates all orders
     * in the plan atomically.
     *
     * @throws OrderImportValidationException when the plan has validation errors
     * @throws OrderImportConflictException when any orderNumber already exists
     * @throws OrderImportProcessingException on unexpected failure (safe message)
     */
    OrderImportConfirmResult confirm(PreparedOrderImportPlan plan);
}
