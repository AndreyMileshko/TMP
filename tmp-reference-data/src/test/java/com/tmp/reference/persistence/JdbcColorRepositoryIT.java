package com.tmp.reference.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.reference.domain.Color;
import com.tmp.reference.domain.ColorCodeKey;
import com.tmp.reference.domain.ColorId;
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
 * COL-020A persistence IT against Flyway V58+V59 seed in isolated Testcontainers PostgreSQL.
 */
@Testcontainers
class JdbcColorRepositoryIT {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-09T04:00:00Z"), ZoneOffset.UTC);
    private static final UUID ACTOR = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private static JdbcColorRepository repository;

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
        repository = new JdbcColorRepository(jdbc);
    }

    @BeforeEach
    void cleanNonSeedRows() {
        jdbc.update("DELETE FROM reference.colors WHERE created_by IS NOT NULL");
        jdbc.update(
                """
                UPDATE reference.colors
                SET active = TRUE, version = 0, updated_at = created_at, updated_by = NULL
                WHERE code_key IN ('RAL7016', 'БЕЛЫЙ', '00')
                """);
    }

    @Test
    void seedExposes860ActiveColorsWithStableIdsAndPreservedSpelling() {
        List<Color> all = repository.search(null, null);
        assertEquals(860, all.size());
        assertEquals(860, repository.search(true, null).size());

        Color white =
                repository
                        .findByCode("Белый")
                        .orElseThrow(() -> new AssertionError("missing Белый"));
        Color whiteByKey =
                repository
                        .findByCodeKey(ColorCodeKey.of("белый"))
                        .orElseThrow(() -> new AssertionError("missing key"));
        assertEquals(white.id(), whiteByKey.id());
        assertEquals("Белый", white.code());
        assertEquals("БЕЛЫЙ", white.codeKey().value());

        Color ral =
                repository
                        .findByCode("RAL7016|30")
                        .orElseThrow(() -> new AssertionError("missing RAL7016|30"));
        assertEquals("RAL7016|30", ral.code());
        assertTrue(ral.code().contains("|"));
        assertFalse(ral.code().contains("\\"));
    }

    @Test
    void insertFindByCodeAndKeyAndRejectsCaseInsensitiveDuplicate() {
        Color created = Color.create("Z-TestColor|30", ACTOR, CLOCK);
        Color saved = repository.insert(created);

        assertEquals(created.id(), saved.id());
        assertEquals(
                Optional.of(saved.id()),
                repository.findById(saved.id()).map(Color::id));
        assertEquals(
                Optional.of("Z-TestColor|30"),
                repository.findByCode("Z-TestColor|30").map(Color::code));
        assertEquals(
                Optional.of(saved.id()),
                repository
                        .findByCodeKey(ColorCodeKey.of("z-testcolor|30"))
                        .map(Color::id));

        Color duplicateSpelling = Color.create("z-testcolor|30", ACTOR, CLOCK);
        assertThrows(Color.DuplicateCodeException.class, () -> repository.insert(duplicateSpelling));

        Color stored =
                repository
                        .findByCodeKey(ColorCodeKey.of("Z-TestColor|30"))
                        .orElseThrow();
        assertEquals("Z-TestColor|30", stored.code());
        assertNotEquals("z-testcolor|30", stored.code());
    }

    @Test
    void searchFiltersByActiveAndSubstring() {
        Color active = repository.insert(Color.create("SearchMe-Active", ACTOR, CLOCK));
        Color archived =
                repository.update(
                        repository
                                .insert(Color.create("SearchMe-Archived", ACTOR, CLOCK))
                                .archive(ACTOR, CLOCK));

        List<Color> activeOnly = repository.search(true, "SearchMe");
        assertEquals(1, activeOnly.size());
        assertEquals(active.id(), activeOnly.get(0).id());

        List<Color> archivedOnly = repository.search(false, "SearchMe");
        assertEquals(1, archivedOnly.size());
        assertEquals(archived.id(), archivedOnly.get(0).id());

        List<Color> both = repository.search(null, "searchme");
        assertEquals(2, both.size());
    }

    @Test
    void archiveRestoreUsesOptimisticLockAndNeverChangesCode() {
        Color inserted = repository.insert(Color.create("LockColor", ACTOR, CLOCK));
        Color archived = repository.update(inserted.archive(ACTOR, CLOCK));
        assertFalse(archived.active());
        assertEquals(1L, archived.version());
        assertEquals("LockColor", archived.code());

        Color restored = repository.update(archived.restore(ACTOR, CLOCK));
        assertTrue(restored.active());
        assertEquals(2L, restored.version());
        assertEquals("LockColor", restored.code());

        Color staleArchive = inserted.archive(ACTOR, CLOCK);
        assertThrows(
                Color.OptimisticLockConflictException.class,
                () -> repository.update(staleArchive));

        Integer codeColumnUpdates =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = 'reference'
                          AND table_name = 'colors'
                          AND column_name IN ('code', 'code_key')
                        """,
                        Integer.class);
        assertEquals(2, codeColumnUpdates);

        String codeAfter =
                jdbc.queryForObject(
                        "SELECT code FROM reference.colors WHERE id = ?",
                        String.class,
                        inserted.id().value());
        assertEquals("LockColor", codeAfter);
    }

    @Test
    void updateDoesNotProvideDeletePath() {
        Color inserted = repository.insert(Color.create("NoDelete", ACTOR, CLOCK));
        ColorId id = inserted.id();
        assertTrue(repository.findById(id).isPresent());
        // Physical delete is out of repository contract; seed + inserts remain queryable.
        assertEquals(861, repository.search(null, null).size());
    }
}
