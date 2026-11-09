package com.tmp.reference.api;

import java.util.List;
import java.util.UUID;

/**
 * Public read API for the TMP color catalog (TMP-SPEC-015 section 17).
 *
 * <p>Requires {@code reference.color.view} before any read.
 */
public interface ColorCatalogQueryApi {

    /**
     * Lists catalog colors with optional active filter and case-insensitive substring search on
     * preserved {@code code}.
     *
     * @param filter ACTIVE / ARCHIVED / ALL
     * @param search blank or {@code null} = no text filter
     */
    List<ColorDto> listColors(ColorDto.ActiveFilter filter, String search);

    /**
     * Returns the number of currently active SO7 mappings that reference the color as inside or
     * outside (archive impact preview).
     */
    long getArchiveImpact(UUID colorId);
}
