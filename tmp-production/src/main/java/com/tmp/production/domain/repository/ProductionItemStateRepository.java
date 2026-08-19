package com.tmp.production.domain.repository;

import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import java.util.Optional;

/**
 * Port for persisting item-owned Production state (Production Spec §5).
 *
 * <p>Adapters store already-valid domain state without applying business rules.
 */
public interface ProductionItemStateRepository {

    /** Persists the given state, inserting or updating by business identity. */
    ProductionItemState save(ProductionItemState state);

    /** Loads state by {@code SourceOrderId + SourceOrderItemId + SpecificationId}. */
    Optional<ProductionItemState> findByIdentity(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            SpecificationId specificationId);
}
