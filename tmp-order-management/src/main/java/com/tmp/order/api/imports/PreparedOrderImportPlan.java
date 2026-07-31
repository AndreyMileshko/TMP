package com.tmp.order.api.imports;

/**
 * Opaque prepared import plan produced only by successful {@link OrderImportService#preview}.
 *
 * <p>External code cannot construct instances. Confirm accepts only plans minted by Import Core.
 */
public interface PreparedOrderImportPlan {

    OrderImportBatch batch();

    String sourceType();

    String sourceReference();

    String contentChecksum();

    String orderNumber();
}
