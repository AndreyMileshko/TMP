package com.tmp.order.application.imports;

import com.tmp.order.api.imports.OrderImportBatch;
import com.tmp.order.api.imports.PreparedOrderImportPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Import-Core-only prepared plan. Package-private constructor ensures instances are created only
 * after successful preview inside this package. Supports multi-order files.
 */
public final class DefaultPreparedOrderImportPlan implements PreparedOrderImportPlan {

    private final List<OrderImportBatch> batches;

    DefaultPreparedOrderImportPlan(OrderImportBatch batch) {
        this(List.of(Objects.requireNonNull(batch, "batch")));
    }

    DefaultPreparedOrderImportPlan(List<OrderImportBatch> batches) {
        Objects.requireNonNull(batches, "batches");
        if (batches.isEmpty()) {
            throw new IllegalArgumentException("batches must not be empty");
        }
        List<OrderImportBatch> copy = new ArrayList<>(batches.size());
        for (OrderImportBatch batch : batches) {
            copy.add(Objects.requireNonNull(batch, "batch"));
        }
        this.batches = List.copyOf(copy);
    }

    @Override
    public List<OrderImportBatch> batches() {
        return batches;
    }

    @Override
    public OrderImportBatch batch() {
        return batches.get(0);
    }

    @Override
    public String sourceType() {
        return batch().sourceType();
    }

    @Override
    public String sourceReference() {
        return batch().sourceReference();
    }

    @Override
    public String contentChecksum() {
        return batch().contentChecksum();
    }

    @Override
    public String orderNumber() {
        return batches.stream()
                .map(OrderImportBatch::orderNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DefaultPreparedOrderImportPlan that)) {
            return false;
        }
        return batches.equals(that.batches);
    }

    @Override
    public int hashCode() {
        return batches.hashCode();
    }
}
