package com.tmp.reference.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.reference.domain.Color;
import com.tmp.reference.domain.ColorId;
import com.tmp.reference.domain.So7ColorMapping;
import com.tmp.reference.domain.So7SourceKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
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
 * COL-021A persistence IT against Flyway V58+V59 seed in isolated Testcontainers PostgreSQL.
 */
@Testcontainers
class JdbcSo7ColorMappingRepositoryIT {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-09T05:00:00Z"), ZoneOffset.UTC);
    private static final UUID ACTOR = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private static JdbcColorRepository colorRepository;
    private static JdbcSo7ColorMappingRepository repository;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(dataSource);
        colorRepository = new JdbcColorRepository(jdbc);
        repository = new JdbcSo7ColorMappingRepository(jdbc);
    }

    @BeforeEach
    void cleanNonSeedRows() {
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
    }

    @Test
    void seedExposes245MappingsWithStableIdsAndPreservedSourceText() {
        List<So7ColorMapping> all = repository.search(null, null);
        assertEquals(245, all.size());
        assertEquals(245, repository.search(true, null).size());

        So7ColorMapping white =
                repository
                        .findBySourceKey(So7SourceKey.of("Белый"))
                        .orElseThrow(() -> new AssertionError("missing Белый mapping"));
        assertEquals("Белый", white.sourceText());
        assertEquals("БЕЛЫЙ", white.sourceKey().value());
        assertEquals(white.insideColorId(), white.outsideColorId());
        assertTrue(white.active());
        assertNotEquals(null, white.id());

        So7ColorMapping slash =
                repository
                        .findBySourceKey(So7SourceKey.of("RAL7016/30т"))
                        .orElseThrow(() -> new AssertionError("missing slash mapping"));
        assertEquals("RAL7016/30т", slash.sourceText());
        assertEquals("RAL7016/30Т", slash.sourceKey().value());

        Color inside =
                colorRepository
                        .findById(slash.insideColorId())
                        .orElseThrow();
        Color outside =
                colorRepository
                        .findById(slash.outsideColorId())
                        .orElseThrow();
        assertEquals("RAL7016|30", inside.code());
        assertEquals("RAL7016|30", outside.code());

        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM reference.so7_color_mappings WHERE source_key = ?",
                        Integer.class,
                        So7SourceKey.of("Без цвета").value()));
    }

    @Test
    void insertFindByKeyRejectsDuplicateNormalizedSourceAndUnknownColorFk() {
        Color inside =
                colorRepository
                        .findByCode("Белый")
                        .orElseThrow();
        Color outside =
                colorRepository
                        .findByCode("RAL7016|30")
                        .orElseThrow();

        So7ColorMapping created =
                So7ColorMapping.create(
                        "  Col021A Unique Source  ",
                        inside.id(),
                        outside.id(),
                        ACTOR,
                        CLOCK);
        So7ColorMapping saved = repository.insert(created);

        assertEquals(created.id(), saved.id());
        assertEquals("Col021A Unique Source", saved.sourceText());
        assertEquals(
                Optional.of(saved.id()),
                repository.findBySourceKey(So7SourceKey.of("col021a unique source")).map(So7ColorMapping::id));
        assertEquals(
                Optional.of(saved.id()),
                repository.findById(saved.id()).map(So7ColorMapping::id));

        So7ColorMapping duplicateSpelling =
                So7ColorMapping.create(
                        "COL021A UNIQUE SOURCE",
                        inside.id(),
                        outside.id(),
                        ACTOR,
                        CLOCK);
        assertThrows(
                So7ColorMapping.DuplicateSourceKeyException.class,
                () -> repository.insert(duplicateSpelling));

        // Existing seed key cannot be overwritten by insert
        So7ColorMapping seedConflict =
                So7ColorMapping.create("Белый", inside.id(), outside.id(), ACTOR, CLOCK);
        assertThrows(
                So7ColorMapping.DuplicateSourceKeyException.class,
                () -> repository.insert(seedConflict));

        ColorId unknown = ColorId.generate();
        So7ColorMapping badFk =
                So7ColorMapping.create(
                        "Col021A Missing Color", unknown, outside.id(), ACTOR, CLOCK);
        assertThrows(IllegalArgumentException.class, () -> repository.insert(badFk));
    }

    @Test
    void searchFiltersByActiveAndSubstringIncludingColorCode() {
        Color white =
                colorRepository
                        .findByCode("Белый")
                        .orElseThrow();
        Color ral =
                colorRepository
                        .findByCode("RAL7016|30")
                        .orElseThrow();

        So7ColorMapping active =
                repository.insert(
                        So7ColorMapping.create(
                                "SearchMe-Active", white.id(), ral.id(), ACTOR, CLOCK));
        So7ColorMapping inactive =
                repository.update(
                        repository
                                .insert(
                                        So7ColorMapping.create(
                                                "SearchMe-Inactive",
                                                ral.id(),
                                                white.id(),
                                                ACTOR,
                                                CLOCK))
                                .deactivate(ACTOR, CLOCK));

        List<So7ColorMapping> activeOnly = repository.search(true, "SearchMe");
        assertEquals(1, activeOnly.size());
        assertEquals(active.id(), activeOnly.get(0).id());

        List<So7ColorMapping> inactiveOnly = repository.search(false, "SearchMe");
        assertEquals(1, inactiveOnly.size());
        assertEquals(inactive.id(), inactiveOnly.get(0).id());

        List<So7ColorMapping> both = repository.search(null, "SearchMe");
        assertEquals(2, both.size());

        List<So7ColorMapping> byCode = repository.search(true, "RAL7016|30");
        assertTrue(byCode.stream().anyMatch(m -> m.id().equals(active.id())));
        assertTrue(
                byCode.stream()
                        .anyMatch(m -> "RAL7016/30т".equals(m.sourceText())));
    }

    @Test
    void updatePairOptimisticLockBulkDeactivateCountAndPhysicalDelete() {
        Color white =
                colorRepository
                        .findByCode("Белый")
                        .orElseThrow();
        Color ral =
                colorRepository
                        .findByCode("RAL7016|30")
                        .orElseThrow();
        Color zero =
                colorRepository
                        .findByCode("00")
                        .orElseThrow();

        So7ColorMapping inserted =
                repository.insert(
                        So7ColorMapping.create(
                                "LockMe Source", white.id(), ral.id(), ACTOR, CLOCK));

        So7ColorMapping pairChanged =
                repository.update(inserted.updatePair(ral.id(), zero.id(), ACTOR, CLOCK));
        assertEquals(1L, pairChanged.version());
        assertEquals(ral.id(), pairChanged.insideColorId());
        assertEquals(zero.id(), pairChanged.outsideColorId());
        assertEquals("LockMe Source", pairChanged.sourceText());

        So7ColorMapping stale = inserted.deactivate(ACTOR, CLOCK);
        assertThrows(
                So7ColorMapping.OptimisticLockConflictException.class,
                () -> repository.update(stale));

        So7ColorMapping second =
                repository.insert(
                        So7ColorMapping.create(
                                "Bulk Side Outside", zero.id(), white.id(), ACTOR, CLOCK));
        long activeForWhite = repository.countActiveByColorId(white.id());
        assertTrue(activeForWhite >= 2);

        int deactivated =
                repository.deactivateByColorId(white.id(), ACTOR, CLOCK.instant());
        assertTrue(deactivated >= 2);
        assertEquals(0L, repository.countActiveByColorId(white.id()));
        // pairChanged no longer references white after updatePair — remains active
        assertTrue(
                repository
                        .findById(pairChanged.id())
                        .orElseThrow()
                        .active());
        assertFalse(
                repository
                        .findById(second.id())
                        .orElseThrow()
                        .active());

        // Seed Белый mapping is deactivated by bulk op — restore for other tests via BeforeEach
        So7ColorMapping toDelete =
                repository
                        .findById(second.id())
                        .orElseThrow();
        repository.delete(toDelete.id(), toDelete.version());
        assertTrue(repository.findById(toDelete.id()).isEmpty());
        assertTrue(
                repository.findBySourceKey(So7SourceKey.of("Bulk Side Outside")).isEmpty());

        assertThrows(
                So7ColorMapping.OptimisticLockConflictException.class,
                () -> repository.delete(pairChanged.id(), pairChanged.version() + 99));

        So7ColorMapping current =
                repository
                        .findById(pairChanged.id())
                        .orElseThrow();
        repository.delete(current.id(), current.version());
        assertTrue(repository.findById(current.id()).isEmpty());

        // Original seed still present (Белый row was only deactivated, not deleted)
        assertEquals(
                245,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM reference.so7_color_mappings", Integer.class));
    }

    @Test
    void sameColorPairMayBeSharedByDifferentSourcesAndInsideEqualsOutsideAllowed() {
        Color white =
                colorRepository
                        .findByCode("Белый")
                        .orElseThrow();

        So7ColorMapping a =
                repository.insert(
                        So7ColorMapping.create(
                                "SharedPair-A", white.id(), white.id(), ACTOR, CLOCK));
        So7ColorMapping b =
                repository.insert(
                        So7ColorMapping.create(
                                "SharedPair-B", white.id(), white.id(), ACTOR, CLOCK));

        assertEquals(a.insideColorId(), a.outsideColorId());
        assertEquals(a.insideColorId(), b.insideColorId());
        assertNotEquals(a.id(), b.id());
        assertNotEquals(a.sourceKey(), b.sourceKey());
    }
}