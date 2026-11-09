package com.tmp.reference.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tmp.reference.api.ImportMappingSaveReport;
import com.tmp.reference.api.So7ColorMappingDto;
import com.tmp.reference.domain.Color;
import com.tmp.reference.domain.ColorId;
import com.tmp.reference.domain.So7ColorMapping;
import com.tmp.reference.domain.So7SourceKey;
import com.tmp.reference.persistence.JdbcColorRepository;
import com.tmp.reference.persistence.JdbcSo7ColorMappingRepository;
import com.tmp.reference.security.ReferenceDataPermissions;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.Login;
import com.tmp.security.api.SessionId;
import com.tmp.security.api.SessionSummary;
import com.tmp.security.api.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * COL-021B application IT: mapping CRUD, import save results, optimistic lock, archive interaction
 * — against Flyway V58+V59 seed in Testcontainers PostgreSQL.
 */
@Testcontainers
class DefaultSo7ColorMappingServiceIT {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-09T09:00:00Z"), ZoneOffset.UTC);
    private static final UUID ACTOR = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcColorRepository colorRepository;
    private static JdbcSo7ColorMappingRepository mappingRepository;

    private AuthorizationService authorization;
    private AuthenticationService authentication;
    private DefaultSo7ColorMappingService mappingService;
    private DefaultColorCatalogService colorService;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
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

        authorization = mock(AuthorizationService.class);
        authentication = mock(AuthenticationService.class);
        when(authentication.currentSession())
                .thenReturn(
                        Optional.of(
                                new SessionSummary(
                                        SessionId.of(UUID.randomUUID()),
                                        UserId.of(ACTOR),
                                        Login.of("mapping-editor"),
                                        CLOCK.instant())));
        DataSourceTransactionManager tx = new DataSourceTransactionManager(dataSource);
        mappingService =
                new DefaultSo7ColorMappingService(
                        authorization,
                        authentication,
                        colorRepository,
                        mappingRepository,
                        tx,
                        CLOCK);
        colorService =
                new DefaultColorCatalogService(
                        authorization,
                        authentication,
                        colorRepository,
                        mappingRepository,
                        tx,
                        CLOCK);
    }

    @Test
    void readsInitialSeedMappingsWithoutMutation() {
        List<So7ColorMappingDto> all =
                mappingService.listMappings(So7ColorMappingDto.ActiveFilter.ACTIVE, null);
        assertEquals(245, all.size());
        assertEquals(
                245L,
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM reference.so7_color_mappings
                        WHERE created_by IS NULL AND active = TRUE
                        """,
                        Long.class));
        assertTrue(
                mappingService
                        .listMappings(So7ColorMappingDto.ActiveFilter.ALL, "Без цвета")
                        .isEmpty());
    }

    @Test
    void createValidMappingAndRejectCaseInsensitiveDuplicate() {
        Color white = requireSeedColor("Белый");
        So7ColorMappingDto created =
                mappingService.createMapping(
                        "Z-SO7 Unique A", white.id().value(), white.id().value());
        assertEquals("Z-SO7 Unique A", created.sourceText());
        assertEquals("Белый", created.displayColor());
        assertTrue(created.active());

        So7ColorMapping.DuplicateSourceKeyException ex =
                assertThrows(
                        So7ColorMapping.DuplicateSourceKeyException.class,
                        () ->
                                mappingService.createMapping(
                                        "z-so7 unique a",
                                        white.id().value(),
                                        white.id().value()));
        assertEquals(DefaultSo7ColorMappingService.DUPLICATE_SOURCE_MESSAGE, ex.getMessage());
    }

    @Test
    void differentSourcesMayShareSameInsideOutsidePair() {
        Color white = requireSeedColor("Белый");
        So7ColorMappingDto first =
                mappingService.createMapping(
                        "Z-SO7 Pair Share 1", white.id().value(), white.id().value());
        So7ColorMappingDto second =
                mappingService.createMapping(
                        "Z-SO7 Pair Share 2", white.id().value(), white.id().value());
        assertEquals(first.insideColorId(), second.insideColorId());
        assertEquals(first.outsideColorId(), second.outsideColorId());
        assertEquals(first.displayColor(), second.displayColor());
    }

    @Test
    void updateMappingPairExplicitlyAndOptimisticLockConflict() {
        Color white = requireSeedColor("Белый");
        Color zero = requireSeedColor("00");
        So7ColorMappingDto created =
                mappingService.createMapping(
                        "Z-SO7 Update Pair", white.id().value(), white.id().value());

        So7ColorMappingDto updated =
                mappingService.updateMappingPair(
                        created.id(),
                        white.id().value(),
                        zero.id().value(),
                        created.version());
        assertEquals("Белый/00", updated.displayColor());
        assertEquals(created.sourceText(), updated.sourceText());

        assertThrows(
                So7ColorMapping.OptimisticLockConflictException.class,
                () ->
                        mappingService.updateMappingPair(
                                created.id(),
                                white.id().value(),
                                white.id().value(),
                                created.version()));
    }

    @Test
    void authorizationDeniedBeforeAnyMutation() {
        doThrow(new AccessDeniedException("no view"))
                .when(authorization)
                .requirePermission(ReferenceDataPermissions.COLOR_VIEW);
        doThrow(new AccessDeniedException("no mapping edit"))
                .when(authorization)
                .requirePermission(ReferenceDataPermissions.COLOR_MAPPING_EDIT);

        assertThrows(
                AccessDeniedException.class,
                () -> mappingService.listMappings(So7ColorMappingDto.ActiveFilter.ALL, "Z-"));
        assertThrows(
                AccessDeniedException.class,
                () ->
                        mappingService.createMapping(
                                "Z-Denied", UUID.randomUUID(), UUID.randomUUID()));
        assertEquals(
                245L,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM reference.so7_color_mappings WHERE created_by IS NULL",
                        Long.class));
    }

    @Test
    void deactivateReactivateAndRejectReactivateWhenColorArchived() {
        Color color =
                colorRepository.insert(Color.create("Z-MapLifecycle", ACTOR, CLOCK));
        So7ColorMappingDto created =
                mappingService.createMapping(
                        "Z-SO7 Lifecycle", color.id().value(), color.id().value());

        So7ColorMappingDto inactive =
                mappingService.setMappingActive(created.id(), false, created.version());
        assertFalse(inactive.active());

        So7ColorMappingDto activeAgain =
                mappingService.setMappingActive(inactive.id(), true, inactive.version());
        assertTrue(activeAgain.active());

        colorService.archiveColor(color.id().value(), color.version());
        So7ColorMapping deactivatedByArchive =
                mappingRepository.findById(created.id()).orElseThrow();
        assertFalse(deactivatedByArchive.active());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mappingService.setMappingActive(
                                deactivatedByArchive.id(),
                                true,
                                deactivatedByArchive.version()));
    }

    @Test
    void physicalDeleteFreesSourceKeyForReuse() {
        Color white = requireSeedColor("Белый");
        So7ColorMappingDto created =
                mappingService.createMapping(
                        "Z-SO7 Delete Me", white.id().value(), white.id().value());

        mappingService.deleteMapping(created.id(), created.version());
        assertTrue(mappingRepository.findById(created.id()).isEmpty());
        assertTrue(
                mappingRepository.findBySourceKey(So7SourceKey.of("Z-SO7 Delete Me")).isEmpty());

        So7ColorMappingDto recreated =
                mappingService.createMapping(
                        "Z-SO7 Delete Me", white.id().value(), white.id().value());
        assertNotNull(recreated.id());
        assertEquals("Z-SO7 Delete Me", recreated.sourceText());
    }

    @Test
    void archiveRestoreColorDoesNotAutoReactivateMappings() {
        Color color =
                colorRepository.insert(Color.create("Z-MapArchive", ACTOR, CLOCK));
        So7ColorMappingDto mapping =
                mappingService.createMapping(
                        "Z-SO7 Archive Side", color.id().value(), color.id().value());
        assertTrue(mapping.active());

        int deactivated = colorService.archiveColor(color.id().value(), color.version());
        assertEquals(1, deactivated);
        So7ColorMapping afterArchive =
                mappingRepository.findById(mapping.id()).orElseThrow();
        assertFalse(afterArchive.active());

        Color archived = colorRepository.findById(color.id()).orElseThrow();
        colorService.restoreColor(color.id().value(), archived.version());
        So7ColorMapping afterRestore =
                mappingRepository.findById(mapping.id()).orElseThrow();
        assertFalse(afterRestore.active());
    }

    @Test
    void saveMappingsFromImportCoversAllResultKindsAndDeduplicatesSamePair() {
        Color white = requireSeedColor("Белый");
        Color zero = requireSeedColor("00");
        Color inactive =
                colorRepository.insert(Color.create("Z-InactiveForImport", ACTOR, CLOCK));
        colorService.archiveColor(inactive.id().value(), inactive.version());

        mappingService.createMapping(
                "Z-SO7 Exists Import", white.id().value(), white.id().value());

        ImportMappingSaveReport report =
                mappingService.saveMappingsFromImport(
                        List.of(
                                new ImportMappingSaveReport.Candidate(
                                        "Z-SO7 New Import",
                                        white.id().value(),
                                        white.id().value()),
                                new ImportMappingSaveReport.Candidate(
                                        "Z-SO7 New Import",
                                        white.id().value(),
                                        white.id().value()),
                                new ImportMappingSaveReport.Candidate(
                                        "Z-SO7 Exists Import",
                                        white.id().value(),
                                        white.id().value()),
                                new ImportMappingSaveReport.Candidate(
                                        "Z-SO7 Conflict",
                                        white.id().value(),
                                        white.id().value()),
                                new ImportMappingSaveReport.Candidate(
                                        "Z-SO7 Conflict",
                                        white.id().value(),
                                        zero.id().value()),
                                new ImportMappingSaveReport.Candidate(
                                        "Z-SO7 Inactive Side",
                                        inactive.id().value(),
                                        white.id().value()),
                                new ImportMappingSaveReport.Candidate(
                                        "", white.id().value(), white.id().value()),
                                new ImportMappingSaveReport.Candidate(
                                        "x".repeat(65),
                                        white.id().value(),
                                        white.id().value())));

        assertEquals(1, report.savedCount());
        assertTrue(
                report.entries().stream()
                        .anyMatch(
                                e ->
                                        e.result()
                                                == ImportMappingSaveReport.Result.SAVED
                                                && "Z-SO7 New Import"
                                                        .equals(e.sourceText())));
        assertTrue(
                report.entries().stream()
                        .anyMatch(
                                e ->
                                        e.result()
                                                == ImportMappingSaveReport.Result
                                                        .NOT_SAVED_MAPPING_EXISTS));
        assertTrue(
                report.entries().stream()
                        .anyMatch(
                                e ->
                                        e.result()
                                                == ImportMappingSaveReport.Result
                                                        .NOT_SAVED_CONFLICT_IN_IMPORT));
        assertTrue(
                report.entries().stream()
                        .anyMatch(
                                e ->
                                        e.result()
                                                == ImportMappingSaveReport.Result
                                                        .NOT_SAVED_COLOR_INACTIVE));
        assertEquals(
                2,
                report.entries().stream()
                        .filter(
                                e ->
                                        e.result()
                                                == ImportMappingSaveReport.Result
                                                        .NOT_SAVED_INVALID_SOURCE)
                        .count());
        assertTrue(
                mappingRepository
                        .findBySourceKey(So7SourceKey.of("Z-SO7 New Import"))
                        .isPresent());
    }

    @Test
    void concurrentCreateSameKeyYieldsExistsForLoser() throws Exception {
        Color white = requireSeedColor("Белый");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Object> first = new AtomicReference<>();
        AtomicReference<Object> second = new AtomicReference<>();

        Runnable worker =
                () -> {
                    try {
                        ready.countDown();
                        start.await();
                        Object outcome =
                                mappingService.saveMappingsFromImport(
                                        List.of(
                                                new ImportMappingSaveReport.Candidate(
                                                        "Z-SO7 Race Key",
                                                        white.id().value(),
                                                        white.id().value())));
                        if (first.compareAndSet(null, outcome)) {
                            return;
                        }
                        second.set(outcome);
                    } catch (Exception ex) {
                        if (!first.compareAndSet(null, ex)) {
                            second.set(ex);
                        }
                    }
                };

        Thread t1 = new Thread(worker);
        Thread t2 = new Thread(worker);
        t1.start();
        t2.start();
        ready.await();
        start.countDown();
        t1.join();
        t2.join();

        ImportMappingSaveReport r1 = (ImportMappingSaveReport) first.get();
        ImportMappingSaveReport r2 = (ImportMappingSaveReport) second.get();
        int saved = r1.savedCount() + r2.savedCount();
        int exists =
                (int)
                        (r1.entries().stream()
                                        .filter(
                                                e ->
                                                        e.result()
                                                                == ImportMappingSaveReport.Result
                                                                        .NOT_SAVED_MAPPING_EXISTS)
                                        .count()
                                + r2.entries().stream()
                                        .filter(
                                                e ->
                                                        e.result()
                                                                == ImportMappingSaveReport.Result
                                                                        .NOT_SAVED_MAPPING_EXISTS)
                                        .count());
        assertEquals(1, saved);
        assertEquals(1, exists);
        assertEquals(
                1L,
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM reference.so7_color_mappings
                        WHERE source_key = ?
                        """,
                        Long.class,
                        So7SourceKey.of("Z-SO7 Race Key").value()));
    }

    @Test
    void technicalFailureRollsBackEntireImportSaveTransaction() {
        Color white = requireSeedColor("Белый");
        DataSourceTransactionManager tx = new DataSourceTransactionManager(dataSource);
        java.util.concurrent.atomic.AtomicInteger inserts =
                new java.util.concurrent.atomic.AtomicInteger();
        com.tmp.reference.domain.So7ColorMappingRepository failingMappings =
                new com.tmp.reference.domain.So7ColorMappingRepository() {
                    @Override
                    public So7ColorMapping insert(So7ColorMapping mapping) {
                        if (inserts.incrementAndGet() == 2) {
                            throw new IllegalStateException("simulated technical failure");
                        }
                        return mappingRepository.insert(mapping);
                    }

                    @Override
                    public So7ColorMapping update(So7ColorMapping mapping) {
                        return mappingRepository.update(mapping);
                    }

                    @Override
                    public Optional<So7ColorMapping> findById(UUID id) {
                        return mappingRepository.findById(id);
                    }

                    @Override
                    public Optional<So7ColorMapping> findBySourceKey(So7SourceKey sourceKey) {
                        return mappingRepository.findBySourceKey(sourceKey);
                    }

                    @Override
                    public List<So7ColorMapping> search(Boolean activeFilter, String searchText) {
                        return mappingRepository.search(activeFilter, searchText);
                    }

                    @Override
                    public int deactivateByColorId(
                            ColorId colorId, UUID updatedBy, java.time.Instant updatedAt) {
                        return mappingRepository.deactivateByColorId(colorId, updatedBy, updatedAt);
                    }

                    @Override
                    public long countActiveByColorId(ColorId colorId) {
                        return mappingRepository.countActiveByColorId(colorId);
                    }

                    @Override
                    public void delete(UUID id, long expectedVersion) {
                        mappingRepository.delete(id, expectedVersion);
                    }
                };
        DefaultSo7ColorMappingService failingService =
                new DefaultSo7ColorMappingService(
                        authorization,
                        authentication,
                        colorRepository,
                        failingMappings,
                        tx,
                        CLOCK);

        assertThrows(
                IllegalStateException.class,
                () ->
                        failingService.saveMappingsFromImport(
                                List.of(
                                        new ImportMappingSaveReport.Candidate(
                                                "Z-SO7 Tech A",
                                                white.id().value(),
                                                white.id().value()),
                                        new ImportMappingSaveReport.Candidate(
                                                "Z-SO7 Tech B",
                                                white.id().value(),
                                                white.id().value()))));

        assertTrue(
                mappingRepository.findBySourceKey(So7SourceKey.of("Z-SO7 Tech A")).isEmpty());
        assertTrue(
                mappingRepository.findBySourceKey(So7SourceKey.of("Z-SO7 Tech B")).isEmpty());
    }

    private static Color requireSeedColor(String code) {
        return colorRepository
                .findByCode(code)
                .orElseThrow(() -> new IllegalStateException("missing seed color " + code));
    }
}