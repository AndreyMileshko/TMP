package com.tmp.reference.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tmp.reference.api.ColorDto;
import com.tmp.reference.api.ResolvedColorPair;
import com.tmp.reference.api.SourceColorResolution;
import com.tmp.reference.domain.Color;
import com.tmp.reference.domain.So7ColorMapping;
import com.tmp.reference.domain.So7SourceKey;
import com.tmp.reference.persistence.JdbcColorMatchingSettingRepository;
import com.tmp.reference.persistence.JdbcColorRepository;
import com.tmp.reference.persistence.JdbcSo7ColorMappingRepository;
import com.tmp.reference.security.ReferenceDataPermissions;
import com.tmp.reference.security.ReferenceImportAccessPolicy;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * COL-022B IT: import resolution against Flyway V58+V59 seed — matching OFF, known/unknown source,
 * Без цвета, normalization, inactive mapping/color, composePair, authorization, no mutations.
 */
@Testcontainers
class DefaultColorImportResolutionServiceIT {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC);
    private static final UUID ACTOR = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcColorRepository colorRepository;
    private static JdbcSo7ColorMappingRepository mappingRepository;
    private static JdbcColorMatchingSettingRepository settingRepository;

    private AuthorizationService authorization;
    private DefaultColorImportResolutionService service;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        dataSource = new DriverManagerDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(dataSource);
        colorRepository = new JdbcColorRepository(jdbc);
        mappingRepository = new JdbcSo7ColorMappingRepository(jdbc);
        settingRepository = new JdbcColorMatchingSettingRepository(jdbc);
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM reference.so7_color_mappings WHERE created_by IS NOT NULL");
        jdbc.update(
                """
                UPDATE reference.so7_color_mappings
                SET active = TRUE, version = 0, updated_at = created_at, updated_by = NULL
                WHERE created_by IS NULL
                """);
        jdbc.update("DELETE FROM reference.colors WHERE created_by IS NOT NULL");
        jdbc.update(
                """
                UPDATE reference.colors
                SET active = TRUE, version = 0, updated_at = created_at, updated_by = NULL
                WHERE created_by IS NULL
                """);
        jdbc.update(
                """
                UPDATE reference.color_matching_settings
                SET automatic_matching_enabled = FALSE,
                    version = 0,
                    updated_at = created_at,
                    updated_by = NULL
                WHERE id = 1
                """);

        authorization = mock(AuthorizationService.class);
        when(authorization.hasPermission(ReferenceDataPermissions.COLOR_VIEW)).thenReturn(true);
        service =
                new DefaultColorImportResolutionService(
                        new ReferenceImportAccessPolicy(authorization),
                        authorization,
                        settingRepository,
                        mappingRepository,
                        colorRepository);
    }

    @Test
    void seedMatchingIsOffAndResolveDoesNotMutate() {
        assertFalse(service.isAutomaticMatchingEnabled());

        long mappingsBefore =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM reference.so7_color_mappings", Long.class);
        long colorsBefore =
                jdbc.queryForObject("SELECT COUNT(*) FROM reference.colors", Long.class);
        long settingsVersionBefore =
                jdbc.queryForObject(
                        "SELECT version FROM reference.color_matching_settings WHERE id = 1",
                        Long.class);

        List<SourceColorResolution> results =
                service.resolveSourceTexts(List.of("RAL7016/30т", "Без цвета"));

        assertEquals(SourceColorResolution.Status.MATCHED, results.get(0).status());
        assertEquals("RAL7016|30", results.get(0).pair().displayColor());
        assertEquals(SourceColorResolution.Status.NO_MAPPING, results.get(1).status());
        assertEquals("БЕЗ ЦВЕТА", results.get(1).sourceKey());

        assertEquals(
                mappingsBefore,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM reference.so7_color_mappings", Long.class));
        assertEquals(
                colorsBefore,
                jdbc.queryForObject("SELECT COUNT(*) FROM reference.colors", Long.class));
        assertEquals(
                settingsVersionBefore,
                jdbc.queryForObject(
                        "SELECT version FROM reference.color_matching_settings WHERE id = 1",
                        Long.class));
        assertFalse(
                Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                """
                                SELECT automatic_matching_enabled
                                FROM reference.color_matching_settings WHERE id = 1
                                """,
                                Boolean.class)));
    }

    @Test
    void matchingOnStillResolvesKnownSourceThroughActiveMapping() {
        jdbc.update(
                """
                UPDATE reference.color_matching_settings
                SET automatic_matching_enabled = TRUE, version = 1
                WHERE id = 1
                """);
        assertTrue(service.isAutomaticMatchingEnabled());

        SourceColorResolution result =
                service.resolveSourceTexts(List.of("RAL7016/30т")).get(0);
        assertEquals(SourceColorResolution.Status.MATCHED, result.status());
        assertEquals("RAL7016|30", result.pair().insideCode());
        assertEquals("RAL7016|30", result.pair().outsideCode());
    }

    @Test
    void normalizationFindsSeedMappingAndPreservesSlashSource() {
        SourceColorResolution spaced =
                service.resolveSourceTexts(List.of(" ral  7016 мат ")).get(0);
        assertEquals(So7SourceKey.of("RAL 7016 мат").value(), spaced.sourceKey());
        assertEquals(" ral  7016 мат ", spaced.sourceText());
        assertEquals(SourceColorResolution.Status.MATCHED, spaced.status());
        assertEquals("RAL7016|30", spaced.pair().displayColor());

        SourceColorResolution slash =
                service.resolveSourceTexts(List.of("RAL7016/30т")).get(0);
        assertEquals(SourceColorResolution.Status.MATCHED, slash.status());
        assertEquals("RAL7016/30Т", slash.sourceKey());
        assertEquals("RAL7016|30", slash.pair().displayColor());
    }

    @Test
    void inactiveMappingYieldsMappingInactive() {
        So7ColorMapping seed =
                mappingRepository
                        .findBySourceKey(So7SourceKey.of("RAL7016/30т"))
                        .orElseThrow();
        mappingRepository.update(seed.deactivate(ACTOR, CLOCK));

        SourceColorResolution result =
                service.resolveSourceTexts(List.of("RAL7016/30т")).get(0);
        assertEquals(SourceColorResolution.Status.MAPPING_INACTIVE, result.status());
    }

    @Test
    void inactiveReferencedColorYieldsMappingInactive() {
        Color color =
                colorRepository
                        .findByCode("RAL7016|30")
                        .orElseThrow(() -> new AssertionError("missing RAL7016|30"));
        colorRepository.update(color.archive(ACTOR, CLOCK));
        // archive path in catalog service deactivates mappings; here we archive color only so
        // mapping may stay active while referenced color is inactive
        SourceColorResolution result =
                service.resolveSourceTexts(List.of("RAL7016/30т")).get(0);
        assertEquals(SourceColorResolution.Status.MAPPING_INACTIVE, result.status());
    }

    @Test
    void batchResolvesMultipleDistinctTextsInOrder() {
        List<SourceColorResolution> results =
                service.resolveSourceTexts(
                        List.of("Без цвета", "RAL7016/30т", "нет-такого-ключа-xyz"));
        assertEquals(3, results.size());
        assertEquals(SourceColorResolution.Status.NO_MAPPING, results.get(0).status());
        assertEquals(SourceColorResolution.Status.MATCHED, results.get(1).status());
        assertEquals(SourceColorResolution.Status.NO_MAPPING, results.get(2).status());
    }

    @Test
    void composePairSameAndDifferentActiveColors() {
        Color white =
                colorRepository.findByCode("Белый").orElseThrow();
        Color ral =
                colorRepository.findByCode("RAL7016|30").orElseThrow();

        ResolvedColorPair same = service.composePair(white.id().value(), white.id().value());
        assertEquals("Белый", same.displayColor());

        ResolvedColorPair different = service.composePair(white.id().value(), ral.id().value());
        assertEquals("Белый/RAL7016|30", different.displayColor());
    }

    @Test
    void composePairRejectsArchivedColor() {
        Color color =
                colorRepository
                        .findByCode("00")
                        .orElseThrow(() -> new AssertionError("missing 00"));
        Color archived = colorRepository.update(color.archive(ACTOR, CLOCK));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.composePair(archived.id().value(), archived.id().value()));
    }

    @Test
    void listActiveColorsExcludesArchived() {
        Color color =
                colorRepository
                        .findByCode("00")
                        .orElseThrow(() -> new AssertionError("missing 00"));
        colorRepository.update(color.archive(ACTOR, CLOCK));

        List<ColorDto> active = service.listActiveColors();
        assertTrue(active.stream().noneMatch(dto -> "00".equals(dto.code())));
        assertTrue(active.stream().allMatch(ColorDto::active));
    }

    @Test
    void fullImportPermissionSetAllowsResolveWithoutReferenceRights() {
        authorization = mock(AuthorizationService.class);
        when(authorization.hasPermission(ReferenceDataPermissions.COLOR_VIEW)).thenReturn(false);
        for (String id : ReferenceImportAccessPolicy.ORDER_IMPORT_PERMISSION_IDS) {
            when(authorization.hasPermission(PermissionId.of(id))).thenReturn(true);
        }
        service =
                new DefaultColorImportResolutionService(
                        new ReferenceImportAccessPolicy(authorization),
                        authorization,
                        settingRepository,
                        mappingRepository,
                        colorRepository);

        SourceColorResolution result =
                service.resolveSourceTexts(List.of("RAL7016/30т")).get(0);
        assertEquals(SourceColorResolution.Status.MATCHED, result.status());
        assertFalse(service.canSaveGlobalMappings());
    }

    @Test
    void onlyOrderCreateIsDenied() {
        authorization = mock(AuthorizationService.class);
        when(authorization.hasPermission(ReferenceDataPermissions.COLOR_VIEW)).thenReturn(false);
        when(authorization.hasPermission(PermissionId.of("order.order.create"))).thenReturn(true);
        when(authorization.hasPermission(PermissionId.of("order.item.create"))).thenReturn(false);
        service =
                new DefaultColorImportResolutionService(
                        new ReferenceImportAccessPolicy(authorization),
                        authorization,
                        settingRepository,
                        mappingRepository,
                        colorRepository);

        assertThrows(
                AccessDeniedException.class,
                () -> service.resolveSourceTexts(List.of("RAL7016/30т")));
    }

    @Test
    void deniedWithoutViewOrImportSet() {
        authorization = mock(AuthorizationService.class);
        when(authorization.hasPermission(ReferenceDataPermissions.COLOR_VIEW)).thenReturn(false);
        for (String id : ReferenceImportAccessPolicy.ORDER_IMPORT_PERMISSION_IDS) {
            when(authorization.hasPermission(PermissionId.of(id))).thenReturn(false);
        }
        service =
                new DefaultColorImportResolutionService(
                        new ReferenceImportAccessPolicy(authorization),
                        authorization,
                        settingRepository,
                        mappingRepository,
                        colorRepository);

        assertThrows(AccessDeniedException.class, service::isAutomaticMatchingEnabled);
        assertThrows(AccessDeniedException.class, service::listActiveColors);
        assertThrows(AccessDeniedException.class, service::canSaveGlobalMappings);
    }
}