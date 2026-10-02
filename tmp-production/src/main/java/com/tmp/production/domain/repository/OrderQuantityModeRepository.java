package com.tmp.production.domain.repository;

import com.tmp.production.domain.OrderQuantityModeOptimisticLockException;
import com.tmp.production.domain.OrderQuantityModeSetting;
import com.tmp.production.domain.ProductionQuantityMode;
import com.tmp.production.domain.SourceOrderId;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Production-owned persistence port for per-order quantity mode settings. */
public interface OrderQuantityModeRepository {

    /** Empty means no stored setting: the order uses the default mode. */
    Optional<OrderQuantityModeSetting> findBySourceOrderId(SourceOrderId sourceOrderId);

    /**
     * Batch load stored modes. Missing ids are omitted (caller applies default STANDARD /
     * version 0).
     */
    Map<SourceOrderId, OrderQuantityModeSetting> findBySourceOrderIds(
            Collection<SourceOrderId> sourceOrderIds);

    /**
     * Stores {@code quantityMode} if the current version equals {@code expectedVersion} ({@code 0}
     * when no setting is stored yet) and returns the persisted setting with the incremented
     * version.
     *
     * @throws OrderQuantityModeOptimisticLockException on version conflict
     */
    OrderQuantityModeSetting save(
            SourceOrderId sourceOrderId, ProductionQuantityMode quantityMode, long expectedVersion);
}
