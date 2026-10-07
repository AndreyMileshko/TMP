package com.tmp.warehouse.api;

import java.util.Collection;
import java.util.Map;
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
}
