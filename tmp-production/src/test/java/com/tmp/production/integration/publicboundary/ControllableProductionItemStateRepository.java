package com.tmp.production.integration.publicboundary;

import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.domain.repository.ProductionItemStateRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test-only decorator around a real JDBC {@link ProductionItemStateRepository}. First N-1 saves
 * delegate; the N-th save throws a controlled exception so the ambient transaction can roll back
 * after a real PostgreSQL write has already occurred.
 */
final class ControllableProductionItemStateRepository implements ProductionItemStateRepository {

    private final ProductionItemStateRepository delegate;
    private final AtomicInteger saveAttempts = new AtomicInteger();
    private volatile int failOnSaveCount = -1;

    ControllableProductionItemStateRepository(ProductionItemStateRepository delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    void failOnSaveCount(int saveCount) {
        this.failOnSaveCount = saveCount;
        this.saveAttempts.set(0);
    }

    void resetFailure() {
        this.failOnSaveCount = -1;
        this.saveAttempts.set(0);
    }

    @Override
    public ProductionItemState save(ProductionItemState state) {
        int attempt = saveAttempts.incrementAndGet();
        if (failOnSaveCount > 0 && attempt >= failOnSaveCount) {
            throw new RuntimeException("controlled production item state save failure");
        }
        return delegate.save(state);
    }

    @Override
    public Optional<ProductionItemState> findByIdentity(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            SpecificationId specificationId) {
        return delegate.findByIdentity(sourceOrderId, sourceOrderItemId, specificationId);
    }

    @Override
    public List<ProductionItemState> findBySourceOrderId(SourceOrderId sourceOrderId) {
        return delegate.findBySourceOrderId(sourceOrderId);
    }

    @Override
    public Optional<ProductionItemState> findBySourceOrderItemId(SourceOrderItemId sourceOrderItemId) {
        return delegate.findBySourceOrderItemId(sourceOrderItemId);
    }

    @Override
    public List<ProductionItemState> findBySourceOrderIdForUpdate(SourceOrderId sourceOrderId) {
        return delegate.findBySourceOrderIdForUpdate(sourceOrderId);
    }
}
