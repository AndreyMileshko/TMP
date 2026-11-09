package com.tmp.reference.application;

import com.tmp.reference.api.ColorDto;
import com.tmp.reference.api.ColorImportResolutionApi;
import com.tmp.reference.api.ResolvedColorPair;
import com.tmp.reference.api.SourceColorResolution;
import com.tmp.reference.domain.Color;
import com.tmp.reference.domain.ColorId;
import com.tmp.reference.domain.ColorMatchingSettingRepository;
import com.tmp.reference.domain.ColorPairComposition;
import com.tmp.reference.domain.ColorRepository;
import com.tmp.reference.domain.So7ColorMapping;
import com.tmp.reference.domain.So7ColorMappingRepository;
import com.tmp.reference.domain.So7SourceKey;
import com.tmp.reference.security.ReferenceDataPermissions;
import com.tmp.reference.security.ReferenceImportAccessPolicy;
import com.tmp.security.api.AuthorizationService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Read-only import color resolution application service (TMP-SPEC-015 sections 6.2, 6.3, 7, 15.2,
 * 17 / COL-022B).
 *
 * <p>Performs batch lookup with at most one mappings scan and one colors scan per {@link
 * #resolveSourceTexts(List)} call. Never mutates catalog, mappings or the matching switch.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed collaborators injected by the container.")
public final class DefaultColorImportResolutionService implements ColorImportResolutionApi {

    private final ReferenceImportAccessPolicy importAccessPolicy;
    private final AuthorizationService authorization;
    private final ColorMatchingSettingRepository settings;
    private final So7ColorMappingRepository mappings;
    private final ColorRepository colors;

    public DefaultColorImportResolutionService(
            ReferenceImportAccessPolicy importAccessPolicy,
            AuthorizationService authorization,
            ColorMatchingSettingRepository settings,
            So7ColorMappingRepository mappings,
            ColorRepository colors) {
        this.importAccessPolicy = Objects.requireNonNull(importAccessPolicy, "importAccessPolicy");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.mappings = Objects.requireNonNull(mappings, "mappings");
        this.colors = Objects.requireNonNull(colors, "colors");
    }

    @Override
    public boolean isAutomaticMatchingEnabled() {
        importAccessPolicy.requireSettingReadAccess();
        return settings.get().automaticMatchingEnabled();
    }

    @Override
    public List<SourceColorResolution> resolveSourceTexts(List<String> sourceTexts) {
        importAccessPolicy.requireSettingReadAccess();
        Objects.requireNonNull(sourceTexts, "sourceTexts");
        for (String sourceText : sourceTexts) {
            requireNonBlankSourceText(sourceText);
        }

        Map<String, So7ColorMapping> mappingsByKey = indexMappingsByKey();
        Map<ColorId, Color> colorsById = indexColorsById();

        List<SourceColorResolution> results = new ArrayList<>(sourceTexts.size());
        for (String sourceText : sourceTexts) {
            So7SourceKey key = So7SourceKey.of(sourceText);
            So7ColorMapping mapping = mappingsByKey.get(key.value());
            if (mapping == null) {
                results.add(SourceColorResolution.noMapping(sourceText, key.value()));
                continue;
            }
            if (!mapping.active()) {
                results.add(SourceColorResolution.mappingInactive(sourceText, key.value()));
                continue;
            }
            Color inside = colorsById.get(mapping.insideColorId());
            Color outside = colorsById.get(mapping.outsideColorId());
            if (inside == null
                    || outside == null
                    || !inside.active()
                    || !outside.active()) {
                results.add(SourceColorResolution.mappingInactive(sourceText, key.value()));
                continue;
            }
            results.add(
                    SourceColorResolution.matched(
                            sourceText,
                            key.value(),
                            toPair(inside, outside)));
        }
        return List.copyOf(results);
    }

    @Override
    public List<ColorDto> listActiveColors() {
        importAccessPolicy.requireSettingReadAccess();
        return colors.search(Boolean.TRUE, null).stream().map(this::toColorDto).toList();
    }

    @Override
    public ResolvedColorPair composePair(UUID insideColorId, UUID outsideColorId) {
        importAccessPolicy.requireSettingReadAccess();
        Color inside =
                requireActiveColor(
                        ColorId.of(Objects.requireNonNull(insideColorId, "insideColorId")));
        Color outside =
                requireActiveColor(
                        ColorId.of(Objects.requireNonNull(outsideColorId, "outsideColorId")));
        return toPair(inside, outside);
    }

    @Override
    public boolean canSaveGlobalMappings() {
        importAccessPolicy.requireSettingReadAccess();
        return authorization.hasPermission(ReferenceDataPermissions.COLOR_MAPPING_EDIT);
    }

    private Map<String, So7ColorMapping> indexMappingsByKey() {
        List<So7ColorMapping> all = mappings.search(null, null);
        Map<String, So7ColorMapping> byKey = new HashMap<>(all.size() * 2);
        for (So7ColorMapping mapping : all) {
            byKey.put(mapping.sourceKey().value(), mapping);
        }
        return byKey;
    }

    private Map<ColorId, Color> indexColorsById() {
        List<Color> all = colors.search(null, null);
        Map<ColorId, Color> byId = new HashMap<>(all.size() * 2);
        for (Color color : all) {
            byId.put(color.id(), color);
        }
        return byId;
    }

    private Color requireActiveColor(ColorId id) {
        Color color =
                colors.findById(id)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Color not found: " + id.value()));
        if (!color.active()) {
            throw new IllegalArgumentException("Referenced color is inactive: " + color.code());
        }
        return color;
    }

    private static void requireNonBlankSourceText(String sourceText) {
        if (sourceText == null || sourceText.isBlank()) {
            throw new IllegalArgumentException(
                    "sourceTexts must contain only non-blank SO7 source texts");
        }
    }

    private static ResolvedColorPair toPair(Color inside, Color outside) {
        return new ResolvedColorPair(
                inside.id().value(),
                outside.id().value(),
                inside.code(),
                outside.code(),
                ColorPairComposition.compose(inside.code(), outside.code()));
    }

    private ColorDto toColorDto(Color color) {
        return new ColorDto(color.id().value(), color.code(), color.active(), color.version());
    }
}
