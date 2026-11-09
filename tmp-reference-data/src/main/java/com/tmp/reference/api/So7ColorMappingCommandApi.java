package com.tmp.reference.api;

import java.util.List;
import java.util.UUID;

/**
 * Public command API for global SO7 color mappings (TMP-SPEC-015 sections 6.4, 11, 17).
 *
 * <p>Requires {@code reference.color-mapping.edit} before any mutation. Physical deletion of a
 * mapping row is permitted and frees the normalized source key. Catalog colors are never deleted.
 */
public interface So7ColorMappingCommandApi {

    /** Creates an active mapping. Both sides must be active catalog colors. */
    So7ColorMappingDto createMapping(String sourceText, UUID insideColorId, UUID outsideColorId);

    /**
     * Explicitly updates the inside/outside pair. Does not change {@code source_text}. Both sides
     * must be active catalog colors.
     */
    So7ColorMappingDto updateMappingPair(
            UUID mappingId, UUID insideColorId, UUID outsideColorId, long expectedVersion);

    /**
     * Activates or deactivates a mapping. Activation requires both referenced colors to be active.
     * Deactivation is always allowed.
     */
    So7ColorMappingDto setMappingActive(UUID mappingId, boolean active, long expectedVersion);

    /**
     * Physically deletes the mapping row and frees its normalized source key (TMP-SPEC-015 section
     * 6.4).
     */
    void deleteMapping(UUID mappingId, long expectedVersion);

    /**
     * Saves mapping candidates after a successful order import confirm (separate Reference Data
     * transaction). Never overwrites an existing mapping. Returns per-key results per section 11.
     */
    ImportMappingSaveReport saveMappingsFromImport(
            List<ImportMappingSaveReport.Candidate> candidates);
}