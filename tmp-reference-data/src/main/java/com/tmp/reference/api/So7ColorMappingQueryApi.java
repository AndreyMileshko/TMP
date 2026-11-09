package com.tmp.reference.api;

import java.util.List;

/**
 * Public read API for global SO7 color mappings (TMP-SPEC-015 section 17).
 *
 * <p>Requires {@code reference.color.view} before any read.
 */
public interface So7ColorMappingQueryApi {

    /**
     * Lists SO7 mappings with optional active filter and case-insensitive substring search on
     * source text and related TMP color codes.
     *
     * @param filter ACTIVE / INACTIVE / ALL
     * @param search blank or {@code null} = no text filter
     */
    List<So7ColorMappingDto> listMappings(So7ColorMappingDto.ActiveFilter filter, String search);
}