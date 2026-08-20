package com.tmp.production.domain.repository;

import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import java.util.List;
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

    /**
     * Loads all item-owned Production states for the given customer order.
     *
     * <p>Returns an empty list when no Production states exist. Does not invent order-level
     * status; aggregation is performed by the domain calculator.
     */
    List<ProductionItemState> findBySourceOrderId(SourceOrderId sourceOrderId);
}
