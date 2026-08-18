package com.tmp.warehouse.api;

/**
 * Resolves extended MaterialReference display information for Warehouse reads.
 *
 * <p>Implementations must not mutate material ownership or create material catalogues.
 */
public interface MaterialReferenceDisplayPort {

    MaterialReferenceDisplay resolve(String materialCode);
}
