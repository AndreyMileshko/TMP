package com.tmp.order.application.imports;

import com.tmp.order.api.imports.OrderImportBatch;
import com.tmp.order.api.imports.PreparedOrderImportPlan;
import java.util.Objects;

/**
 * Import-Core-only prepared plan. Package-private constructor ensures instances are created only
 * after successful preview inside this package.
 */
public final class DefaultPreparedOrderImportPlan implements PreparedOrderImportPlan {

    private final OrderImportBatch batch;

    DefaultPreparedOrderImportPlan(OrderImportBatch batch) {
        this.batch = Objects.requireNonNull(batch, "batch");
    }

    @Override
    public OrderImportBatch batch() {
        return batch;
    }

    @Override
    public String sourceType() {
        return batch.sourceType();
    }

    @Override
    public String sourceReference() {
        return batch.sourceReference();
    }

    @Override
    public String contentChecksum() {
        return batch.contentChecksum();
    }

    @Override
    public String orderNumber() {
        return batch.orderNumber();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DefaultPreparedOrderImportPlan that)) {
            return false;
        }
        return batch.equals(that.batch);
    }

    @Override
    public int hashCode() {
        return batch.hashCode();
    }
}
