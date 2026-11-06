package com.tmp.warehouse.api;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only projection: Warehouse operational tasks → human-readable Production order number(s).
 *
 * <p>UUIDs are external references only (no FK). Cross-schema joins for display belong at the
 * composition/bootstrap boundary. Missing links return no entry (UI shows "—"). Never returns
 * technical UUIDs as order numbers.
 *
 * <p>Cross-order Material Requirements compose unique order numbers as a single deterministic
 * comma-separated value (full string; UI may ellipsis visually).
 */
public interface TransferDocumentOrderReferenceQuery {

    /**
     * @return map of warehouse transfer document id → authoritative order number composition
     *     (never UUID)
     */
    Map<UUID, String> findOrderNumbersByDocumentIds(Collection<UUID> documentIds);

    /**
     * @return map of warehouse demand id → authoritative order number composition (never UUID)
     */
    Map<UUID, String> findOrderNumbersByDemandIds(Collection<UUID> demandIds);

    /**
     * Reverse lookup for Warehouse History search: transfer document ids whose related production
     * order number(s) match {@code orderNumberSearch} (trim, case-insensitive substring). Empty
     * when the search is blank or no persisted Demand/MR relationships match. Never fabricates
     * document ids.
     */
    Set<UUID> findDocumentIdsByOrderNumberSearch(String orderNumberSearch);
}
