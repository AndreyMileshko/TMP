package com.tmp.reference.api;

import java.util.UUID;

/**
 * Public command API for the TMP color catalog (TMP-SPEC-015 section 17).
 *
 * <p>Requires {@code reference.color.edit} before any mutation. Physical deletion and code changes
 * are not part of this contract.
 */
public interface ColorCatalogCommandApi {

    /** Creates a new active catalog color. Code spelling is preserved after trim. */
    ColorDto createColor(String code);

    /**
     * Archives the color and deactivates all active SO7 mappings that reference it (inside or
     * outside) in one Reference Data transaction.
     *
     * @return number of mappings deactivated
     */
    int archiveColor(UUID colorId, long expectedVersion);

    /**
     * Restores an archived color to active. Previously deactivated SO7 mappings are not
     * reactivated.
     */
    ColorDto restoreColor(UUID colorId, long expectedVersion);
}
