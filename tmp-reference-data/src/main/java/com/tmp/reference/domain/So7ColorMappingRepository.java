package com.tmp.reference.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for global SO7 color mappings ({@code reference.so7_color_mappings}).
 *
 * <p>Supports physical deletion of mapping rows (TMP-SPEC-015 section 6.4). Catalog colors are not
 * deleted through this port.
 */
public interface So7ColorMappingRepository {

    /**
     * Inserts a new mapping. Rejects duplicate normalized source keys via {@link
     * So7ColorMapping.DuplicateSourceKeyException}.
     */
    So7ColorMapping insert(So7ColorMapping mapping);

    /**
     * Persists pair / active / audit changes with optimistic locking. Never updates {@code
     * source_text} / {@code source_key}.
     */
    So7ColorMapping update(So7ColorMapping mapping);

    Optional<So7ColorMapping> findById(UUID id);

    Optional<So7ColorMapping> findBySourceKey(So7SourceKey sourceKey);

    /**
     * Lists mappings with optional active filter and case-insensitive substring search on source
     * text and related TMP color codes.
     *
     * @param activeFilter {@code null} = all; {@code true}/{@code false} = filter by {@code active}
     * @param searchText blank or {@code null} = no text filter
     */
    List<So7ColorMapping> search(Boolean activeFilter, String searchText);

    /**
     * Deactivates every currently active mapping that references {@code colorId} as inside or
     * outside. Increments {@code version} for each updated row. Used by later archive orchestration.
     *
     * @return number of mappings deactivated
     */
    int deactivateByColorId(ColorId colorId, UUID updatedBy, Instant updatedAt);

    /** Counts active mappings that reference {@code colorId} as inside or outside. */
    long countActiveByColorId(ColorId colorId);

    /**
     * Physically deletes the mapping row and frees its source key. Rejects version mismatch via
     * {@link So7ColorMapping.OptimisticLockConflictException}.
     */
    void delete(UUID id, long expectedVersion);
}