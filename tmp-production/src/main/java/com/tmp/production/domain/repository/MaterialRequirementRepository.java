package com.tmp.production.domain.repository;

import com.tmp.production.domain.MaterialRequirement;
import com.tmp.production.domain.MaterialRequirementId;
import com.tmp.production.domain.MaterialRequirementOptimisticLockException;
import com.tmp.production.domain.MaterialRequirementSourceItemKey;
import com.tmp.production.domain.SourceOrderItemId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Production-owned persistence port for editable Material Requirements. */
public interface MaterialRequirementRepository {

    /**
     * Inserts or updates the requirement. On update, uses optimistic {@code version} and throws
     * {@link MaterialRequirementOptimisticLockException} on conflict.
     */
    MaterialRequirement save(MaterialRequirement requirement);

    Optional<MaterialRequirement> findById(MaterialRequirementId id);

    /**
     * Loads the requirement with a {@code SELECT … FOR UPDATE} header row lock so a concurrent
     * Submit is serialized (Stage 3.5.10). Must be called inside the caller's transaction; the lock
     * is held until that outer transaction commits.
     */
    Optional<MaterialRequirement> findByIdForUpdate(MaterialRequirementId id);

    /**
     * Persists a {@code DRAFT → SUBMITTED} transition (header only: status, submission metadata,
     * version increment) using optimistic {@code version}. Lines and source items are unchanged.
     * Must run inside the caller's transaction.
     *
     * @throws MaterialRequirementOptimisticLockException on version conflict
     */
    MaterialRequirement markSubmitted(MaterialRequirement requirement);

    /**
     * Sums {@code requested_product_quantity} from {@code SUBMITTED} requirements for the given
     * source items. Missing keys mean zero submitted coverage. DRAFT rows are ignored.
     */
    Map<MaterialRequirementSourceItemKey, Long> sumSubmittedProductQuantities(
            Collection<MaterialRequirementSourceItemKey> keys);

    /** Batch load by ids; missing ids are omitted. */
    List<MaterialRequirement> findByIds(Collection<MaterialRequirementId> ids);

    /**
     * Batch load requirements that reference any of the given Order Item ids (any status).
     * Ordered by created_at ascending for stable reopen/list behaviour.
     */
    List<MaterialRequirement> findBySourceOrderItemIds(Collection<SourceOrderItemId> itemIds);

    /**
     * Lists persisted DRAFT Material Requirements newest-first for reopen UX. Does not include
     * SUBMITTED rows.
     */
    List<MaterialRequirement> findDraftsNewestFirst();
}
