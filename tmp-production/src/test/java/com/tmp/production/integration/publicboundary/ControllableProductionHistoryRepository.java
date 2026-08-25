package com.tmp.production.integration.publicboundary;

import com.tmp.production.domain.ProductionHistoryEntry;
import com.tmp.production.domain.ProductionHistoryEntry.ProductionHistoryType;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.repository.ProductionHistoryRepository;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test-only decorator around a real JDBC history repository. Can fail on the first append of a
 * chosen history type so Release+Consumption joins the same ambient rollback.
 */
final class ControllableProductionHistoryRepository implements ProductionHistoryRepository {

    private final ProductionHistoryRepository delegate;
    private volatile ProductionHistoryType failOnType;
    private final AtomicBoolean failedOnce = new AtomicBoolean();

    ControllableProductionHistoryRepository(ProductionHistoryRepository delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    void failOnFirstAppendOf(ProductionHistoryType type) {
        this.failOnType = type;
        this.failedOnce.set(false);
    }

    void resetFailure() {
        this.failOnType = null;
        this.failedOnce.set(false);
    }

    @Override
    public ProductionHistoryEntry append(ProductionHistoryEntry entry) {
        ProductionHistoryType target = failOnType;
        if (target != null
                && entry.historyType() == target
                && failedOnce.compareAndSet(false, true)) {
            throw new RuntimeException("controlled production history append failure");
        }
        return delegate.append(entry);
    }

    @Override
    public List<ProductionHistoryEntry> listByOrder(SourceOrderId sourceOrderId) {
        return delegate.listByOrder(sourceOrderId);
    }
}
