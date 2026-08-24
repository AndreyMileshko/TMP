package com.tmp.production.domain.repository;

import com.tmp.production.domain.ProductionHistoryEntry;
import com.tmp.production.domain.SourceOrderId;
import java.util.List;

/**
 * Append-only Production-owned business history store (Production Spec §22, ADR-021).
 *
 * <p>Update and delete are intentionally absent from the main API.
 */
public interface ProductionHistoryRepository {

    /**
     * Appends an immutable history entry. Joins the ambient transaction when present.
     *
     * @return the appended entry
     */
    ProductionHistoryEntry append(ProductionHistoryEntry entry);

    /**
     * Returns history for one order in deterministic chronological order:
     * {@code occurred_at ASC}, {@code recorded_at ASC}, {@code entry_id ASC}.
     *
     * <p>Unknown orders yield an empty immutable list (Production owns history; OM is not queried).
     */
    List<ProductionHistoryEntry> listByOrder(SourceOrderId sourceOrderId);
}
