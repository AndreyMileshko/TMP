package com.tmp.reference.api;

import java.util.List;
import java.util.UUID;

/**
 * Read-only import resolution API for SO7 source color texts (TMP-SPEC-015 sections 6.2, 6.3, 7,
 * 15.2, 17 / COL-022B).
 *
 * <p>Does not create orders, mutate imported specifications, or save global mappings. Access
 * follows section 15.2 ({@code reference.color.view} or the full Order Management import permission
 * set). {@link #canSaveGlobalMappings()} additionally reflects {@code
 * reference.color-mapping.edit}.
 *
 * <p>Automatic matching OFF/ON is reported by {@link #isAutomaticMatchingEnabled()}; callers decide
 * whether to substitute colors. {@link #resolveSourceTexts(List)} always performs key lookup.
 */
public interface ColorImportResolutionApi {

    /** Current value of the system-wide automatic matching switch. */
    boolean isAutomaticMatchingEnabled();

    /**
     * Resolves each non-empty source text against global SO7 mappings.
     *
     * <p>Null or blank texts must not be passed. Results preserve caller order. Matching requires
     * an active mapping whose inside and outside catalog colors are both active.
     */
    List<SourceColorResolution> resolveSourceTexts(List<String> sourceTexts);

    /** Active TMP catalog colors available for manual pair selection. */
    List<ColorDto> listActiveColors();

    /**
     * Composes the display color for two active catalog colors (TMP-SPEC-015 section 6.3). Rejects
     * missing or archived colors.
     */
    ResolvedColorPair composePair(UUID insideColorId, UUID outsideColorId);

    /**
     * {@code true} when the current session may save global mappings from import ({@code
     * reference.color-mapping.edit}).
     */
    boolean canSaveGlobalMappings();
}
