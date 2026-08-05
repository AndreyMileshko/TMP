package com.tmp.order.api.imports;

import java.util.List;

/**
 * Opaque prepared import plan produced only by successful {@link OrderImportService#preview}.
 *
 * <p>External code cannot construct instances. Confirm accepts only plans minted by Import Core.
 * One plan may contain several orders from a single STXT file.
 */
public interface PreparedOrderImportPlan {

    /** All orders to import (one or more). */
    List<OrderImportBatch> batches();

    /** First batch (convenience for single-order callers). */
    OrderImportBatch batch();

    String sourceType();

    String sourceReference();

    String contentChecksum();

    /** Comma-separated order numbers when multiple; otherwise the single number. */
    String orderNumber();
}
