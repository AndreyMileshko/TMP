package com.tmp.production.domain.repository;

import com.tmp.production.domain.ProductionRelease;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for Production-owned Release document payload (ADR-028).
 */
public interface ProductionReleaseRepository {

    /**
     * Inserts or replaces a DRAFT release. Rejected when the existing row is already posted.
     */
    ProductionRelease saveDraft(ProductionRelease release);

    /**
     * Persists posted flag and forbids further draft replacement.
     */
    ProductionRelease markPosted(ProductionRelease release);

    Optional<ProductionRelease> findByDocumentId(UUID documentId);
}
