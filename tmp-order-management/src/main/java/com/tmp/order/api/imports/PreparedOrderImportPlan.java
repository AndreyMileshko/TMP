package com.tmp.order.api.imports;

import java.util.Objects;

/**
 * Immutable validated import plan returned by preview when there are no errors. Confirm re-checks
 * critical invariants because database state may have changed after preview.
 */
public final class PreparedOrderImportPlan {

    private final OrderImportBatch batch;

    private PreparedOrderImportPlan(OrderImportBatch batch) {
        this.batch = batch;
    }

    /**
     * Creates a plan from an already-validated batch snapshot. Callers outside Import Core should
     * obtain plans only via {@link OrderImportPreview#preparedPlan()}.
     */
    public static PreparedOrderImportPlan fromValidatedBatch(OrderImportBatch batch) {
        Objects.requireNonNull(batch, "batch");
        return new PreparedOrderImportPlan(batch);
    }

    public OrderImportBatch batch() {
        return batch;
    }

    public String sourceType() {
        return batch.sourceType();
    }

    public String sourceReference() {
        return batch.sourceReference();
    }

    public String contentChecksum() {
        return batch.contentChecksum();
    }

    public String orderNumber() {
        return batch.orderNumber();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PreparedOrderImportPlan that)) {
            return false;
        }
        return batch.equals(that.batch);
    }

    @Override
    public int hashCode() {
        return batch.hashCode();
    }
}
