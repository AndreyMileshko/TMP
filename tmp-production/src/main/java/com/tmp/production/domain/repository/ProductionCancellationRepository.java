package com.tmp.production.domain.repository;

import com.tmp.production.domain.ProductionCancellation;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for Production-owned Cancellation document payload (ADR-028).
 */
public interface ProductionCancellationRepository {

    /** Inserts or replaces a DRAFT cancellation. Rejected when the existing row is posted. */
    ProductionCancellation saveDraft(ProductionCancellation cancellation);

    /** Persists posted flag and forbids further draft replacement. */
    ProductionCancellation markPosted(ProductionCancellation cancellation);

    Optional<ProductionCancellation> findByDocumentId(UUID documentId);
}
