package com.tmp.warehouse.domain.repository;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only projection: Warehouse Transfer Document → Material Requirement → Order number.
 *
 * <p>UUIDs are external references only (no FK). Implementations may join Production / Order
 * Management schemas for display. Missing links return no entry (UI shows "—").
 */
public interface TransferDocumentOrderReferenceQuery {

    /**
     * @return map of warehouse document id → authoritative order number (never UUID)
     */
    Map<UUID, String> findOrderNumbersByDocumentIds(Collection<UUID> documentIds);
}
