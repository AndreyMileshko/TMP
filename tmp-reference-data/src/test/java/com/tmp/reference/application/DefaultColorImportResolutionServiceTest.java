package com.tmp.reference.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tmp.reference.api.ColorDto;
import com.tmp.reference.api.ResolvedColorPair;
import com.tmp.reference.api.SourceColorResolution;
import com.tmp.reference.domain.Color;
import com.tmp.reference.domain.ColorId;
import com.tmp.reference.domain.ColorMatchingSettingRepository;
import com.tmp.reference.domain.ColorMatchingSettingRepository.ColorMatchingSetting;
import com.tmp.reference.domain.ColorRepository;
import com.tmp.reference.domain.So7ColorMapping;
import com.tmp.reference.domain.So7ColorMappingRepository;
import com.tmp.reference.domain.So7SourceKey;
import com.tmp.reference.security.ReferenceDataPermissions;
import com.tmp.reference.security.ReferenceImportAccessPolicy;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultColorImportResolutionServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-09T08:00:00Z"), ZoneOffset.UTC);
    private static final UUID ACTOR = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock private ReferenceImportAccessPolicy importAccessPolicy;
    @Mock private AuthorizationService authorization;
    @Mock private ColorMatchingSettingRepository settings;
    @Mock private So7ColorMappingRepository mappings;
    @Mock private ColorRepository colors;

    private DefaultColorImportResolutionService service;

    @BeforeEach
    void setUp() {
        service =
                new DefaultColorImportResolutionService(
                        importAccessPolicy, authorization, settings, mappings, colors);
    }

    @Test
    void operationsRequireSettingReadAccess() {
        doThrow(new AccessDeniedException("denied"))
                .when(importAccessPolicy)
                .requireSettingReadAccess();

        assertThrows(AccessDeniedException.class, service::isAutomaticMatchingEnabled);
        assertThrows(
                AccessDeniedException.class, () -> service.resolveSourceTexts(List.of("Белый")));
        assertThrows(AccessDeniedException.class, service::listActiveColors);
        assertThrows(
                AccessDeniedException.class,
                () -> service.composePair(UUID.randomUUID(), UUID.randomUUID()));
        assertThrows(AccessDeniedException.class, service::canSaveGlobalMappings);
        verifyNoInteractions(settings, mappings, colors);
    }

    @Test
    void matchingOffReportedFromSettingWithoutMutation() {
        when(settings.get()).thenReturn(setting(false, 0L));

        assertFalse(service.isAutomaticMatchingEnabled());
        verify(importAccessPolicy).requireSettingReadAccess();
        verify(settings).get();
        verify(settings, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resolveMatchedActiveMappingWithActiveColors() {
        Color color = Color.create("RAL7016|30", ACTOR, CLOCK);
        So7ColorMapping mapping =
                So7ColorMapping.create("RAL7016/30т", color.id(), color.id(), ACTOR, CLOCK);
        when(mappings.search(null, null)).thenReturn(List.of(mapping));
        when(colors.search(null, null)).thenReturn(List.of(color));

        List<SourceColorResolution> results =
                service.resolveSourceTexts(List.of("RAL7016/30т", " ral7016/30Т "));

        assertEquals(2, results.size());
        for (SourceColorResolution result : results) {
            assertEquals(SourceColorResolution.Status.MATCHED, result.status());
            assertEquals("RAL7016/30Т", result.sourceKey());
            assertEquals("RAL7016|30", result.pair().displayColor());
            assertEquals("RAL7016|30", result.pair().insideCode());
            assertEquals("RAL7016|30", result.pair().outsideCode());
        }
        verify(mappings).search(null, null);
        verify(colors).search(null, null);
        verify(mappings, never()).insert(org.mockito.ArgumentMatchers.any());
        verify(mappings, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resolveDifferentInsideOutsideProducesSlashDisplay() {
        Color inside = Color.create("Белый", ACTOR, CLOCK);
        Color outside = Color.create("RAL7016", ACTOR, CLOCK);
        So7ColorMapping mapping =
                So7ColorMapping.create("Белый/RAL7016", inside.id(), outside.id(), ACTOR, CLOCK);
        when(mappings.search(null, null)).thenReturn(List.of(mapping));
        when(colors.search(null, null)).thenReturn(List.of(inside, outside));

        SourceColorResolution result =
                service.resolveSourceTexts(List.of("Белый/RAL7016")).get(0);

        assertEquals(SourceColorResolution.Status.MATCHED, result.status());
        assertEquals("Белый/RAL7016", result.pair().displayColor());
        assertEquals(inside.id().value(), result.pair().insideColorId());
        assertEquals(outside.id().value(), result.pair().outsideColorId());
    }

    @Test
    void resolveUnknownAndBezCvetaAreNoMapping() {
        when(mappings.search(null, null)).thenReturn(List.of());
        when(colors.search(null, null)).thenReturn(List.of());

        List<SourceColorResolution> results =
                service.resolveSourceTexts(List.of("Без цвета", "Неизвестный"));

        assertEquals(SourceColorResolution.Status.NO_MAPPING, results.get(0).status());
        assertEquals("БЕЗ ЦВЕТА", results.get(0).sourceKey());
        assertNull(results.get(0).pair());
        assertEquals(SourceColorResolution.Status.NO_MAPPING, results.get(1).status());
    }

    @Test
    void resolveInactiveMappingAndInactiveColorAreMappingInactive() {
        Color active = Color.create("Активный", ACTOR, CLOCK);
        Color archived = Color.create("Архив", ACTOR, CLOCK).archive(ACTOR, CLOCK);
        So7ColorMapping inactive =
                So7ColorMapping.create("inactive-src", active.id(), active.id(), ACTOR, CLOCK)
                        .deactivate(ACTOR, CLOCK);
        So7ColorMapping activeWithArchived =
                So7ColorMapping.create("archived-ref", archived.id(), archived.id(), ACTOR, CLOCK);
        when(mappings.search(null, null)).thenReturn(List.of(inactive, activeWithArchived));
        when(colors.search(null, null)).thenReturn(List.of(active, archived));

        List<SourceColorResolution> results =
                service.resolveSourceTexts(List.of("inactive-src", "archived-ref"));

        assertEquals(SourceColorResolution.Status.MAPPING_INACTIVE, results.get(0).status());
        assertEquals(SourceColorResolution.Status.MAPPING_INACTIVE, results.get(1).status());
    }

    @Test
    void resolveRejectsNullAndBlankSourceTexts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.resolveSourceTexts(List.of("ok", "  ")));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.resolveSourceTexts(java.util.Arrays.asList("ok", null)));
        verifyNoInteractions(mappings, colors);
    }

    @Test
    void resolveNormalizesCaseWhitespaceAndPreservesSlashSuffix() {
        Color color = Color.create("RAL 7016 мат", ACTOR, CLOCK);
        So7ColorMapping mapping =
                So7ColorMapping.create("RAL 7016 мат", color.id(), color.id(), ACTOR, CLOCK);
        when(mappings.search(null, null)).thenReturn(List.of(mapping));
        when(colors.search(null, null)).thenReturn(List.of(color));

        SourceColorResolution result =
                service.resolveSourceTexts(List.of(" ral  7016 мат ")).get(0);

        assertEquals(SourceColorResolution.Status.MATCHED, result.status());
        assertEquals(So7SourceKey.of("RAL 7016 мат").value(), result.sourceKey());
        assertEquals("RAL 7016 мат", result.pair().displayColor());
    }

    @Test
    void listActiveColorsReturnsOnlyActive() {
        Color active = Color.create("Акт", ACTOR, CLOCK);
        when(colors.search(Boolean.TRUE, null)).thenReturn(List.of(active));

        List<ColorDto> listed = service.listActiveColors();

        assertEquals(1, listed.size());
        assertEquals("Акт", listed.get(0).code());
        assertTrue(listed.get(0).active());
    }

    @Test
    void composePairRejectsArchivedAndComposesActive() {
        Color inside = Color.create("Внутри", ACTOR, CLOCK);
        Color outside = Color.create("Снаружи", ACTOR, CLOCK);
        Color archived = Color.create("Архив", ACTOR, CLOCK).archive(ACTOR, CLOCK);
        when(colors.findById(inside.id())).thenReturn(Optional.of(inside));
        when(colors.findById(outside.id())).thenReturn(Optional.of(outside));
        when(colors.findById(archived.id())).thenReturn(Optional.of(archived));

        ResolvedColorPair pair = service.composePair(inside.id().value(), outside.id().value());
        assertEquals("Внутри/Снаружи", pair.displayColor());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.composePair(archived.id().value(), outside.id().value()));
    }

    @Test
    void canSaveGlobalMappingsReflectsMappingEditPermission() {
        when(authorization.hasPermission(ReferenceDataPermissions.COLOR_MAPPING_EDIT))
                .thenReturn(true);
        assertTrue(service.canSaveGlobalMappings());

        when(authorization.hasPermission(ReferenceDataPermissions.COLOR_MAPPING_EDIT))
                .thenReturn(false);
        assertFalse(service.canSaveGlobalMappings());
    }

    private static ColorMatchingSetting setting(boolean enabled, long version) {
        Instant now = CLOCK.instant();
        return ColorMatchingSetting.rehydrate(enabled, version, now, now, null, null);
    }
}