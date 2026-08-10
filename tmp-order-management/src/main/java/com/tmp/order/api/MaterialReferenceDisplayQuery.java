package com.tmp.order.api;

import java.util.Optional;

/**
 * Read-only lookup of material display fields from ACTIVE Specification context (ADR-032).
 *
 * <p>Cross-capability wiring (e.g. Warehouse material display enrichment) uses this contract.
 * Not a Material Master — display projection only.
 */
public interface MaterialReferenceDisplayQuery {

    Optional<MaterialReferenceDisplayDto> findByMaterialCode(String materialCode);
}
