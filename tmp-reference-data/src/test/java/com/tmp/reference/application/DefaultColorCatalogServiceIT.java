package com.tmp.reference.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tmp.reference.api.ColorDto;
import com.tmp.reference.domain.Color;
import com.tmp.reference.domain.ColorId;
import com.tmp.reference.domain.So7ColorMapping;
import com.tmp.reference.domain.So7ColorMappingRepository;
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
 * COL-020B application IT: permissions, create/duplicate, atomic archive, restore without mapping
 * reactivation, optimistic locking — against Flyway V58+V59 seed in Testcontainers PostgreSQL.
 */
@Testcontainers
class DefaultColorCatalogServiceIT {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-09T07:00:00Z"), ZoneOffset.UTC);
    private static final UUID ACTOR = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcColorRepository colorRepository;
    private static JdbcSo7ColorMappingRepository mappingRepository;

    private AuthorizationService authorization;
    private AuthenticationService authentication;
    private DefaultColorCatalogService service;

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
                                        Login.of("color-editor"),
                                        CLOCK.instant())));
        service =
                new DefaultColorCatalogService(
                        authorization,
                        authentication,
                        colorRepository,
                        mappingRepository,
                        new DataSourceTransactionManager(dataSource),
                        CLOCK);
    }

    @Test
    void createColorSucceedsAndRejectsCaseInsensitiveDuplicate() {
        ColorDto created = service.createColor("Z-AppColor|30");
        assertEquals("Z-AppColor|30", created.code());
        assertTrue(created.active());

        assertThrows(Color.DuplicateCodeException.class, () -> service.createColor("z-appcolor|30"));

        Color stored =
                colorRepository
                        .findById(ColorId.of(created.id()))
                        .orElseThrow();
        assertEquals("Z-AppColor|30", stored.code());
        assertNotEquals("z-appcolor|30", stored.code());
    }

    @Test
    void authorizationDeniedBeforeAnyReadOrWrite() {
        doThrow(new AccessDeniedException("no view"))
                .when(authorization)
                .requirePermission(ReferenceDataPermissions.COLOR_VIEW);
        doThrow(new AccessDeniedException("no edit"))
                .when(authorization)
                .requirePermission(ReferenceDataPermissions.COLOR_EDIT);

        assertThrows(
                AccessDeniedException.class,
                () -> service.listColors(ColorDto.ActiveFilter.ALL, "Z-"));
        assertThrows(AccessDeniedException.class, () -> service.createColor("Z-Denied"));
        assertEquals(
                860L,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM reference.colors WHERE created_by IS NULL",
                        Long.class));
    }

    @Test
    void archiveWithoutMappingsAndRestoreDoesNotReactivateAnything() {
        ColorDto created = service.createColor("Z-NoMap");
        assertEquals(0L, service.getArchiveImpact(created.id()));

        int deactivated = service.archiveColor(created.id(), created.version());
        assertEquals(0, deactivated);

        ColorDto archived =
                service.listColors(ColorDto.ActiveFilter.ARCHIVED, "Z-NoMap").get(0);
        assertFalse(archived.active());

        ColorDto restored = service.restoreColor(archived.id(), archived.version());
        assertTrue(restored.active());
        assertEquals(
                245L,
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM reference.so7_color_mappings
                        WHERE active = TRUE AND created_by IS NULL
                        """,
                        Long.class));
    }

    @Test
    void archiveWithMappingsDeactivatesAtomicallyAndRestoreLeavesMappingsInactive() {
        ColorDto color = service.createColor("Z-WithMap");
        ColorId colorId = ColorId.of(color.id());
        mappingRepository.insert(
                So7ColorMapping.create("SO7 Z-WithMap A", colorId, colorId, ACTOR, CLOCK));
        Color other =
                colorRepository.insert(Color.create("Z-OtherSide", ACTOR, CLOCK));
        mappingRepository.insert(
                So7ColorMapping.create(
                        "SO7 Z-WithMap B", colorId, other.id(), ACTOR, CLOCK));

        assertEquals(2L, service.getArchiveImpact(color.id()));

        int deactivated = service.archiveColor(color.id(), color.version());
        assertEquals(2, deactivated);

        Color archived = colorRepository.findById(colorId).orElseThrow();
        assertFalse(archived.active());
        assertEquals(0L, mappingRepository.countActiveByColorId(colorId));
        assertEquals(
                2,
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM reference.so7_color_mappings
                        WHERE created_by = ? AND active = FALSE
                        """,
                        Integer.class,
                        ACTOR));

        ColorDto restored = service.restoreColor(color.id(), archived.version());
        assertTrue(restored.active());
        assertEquals(0L, mappingRepository.countActiveByColorId(colorId));
        assertEquals(
                2,
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM reference.so7_color_mappings
                        WHERE created_by = ? AND active = FALSE
                        """,
                        Integer.class,
                        ACTOR));
    }

    @Test
    void archiveRollsBackColorAndMappingsWhenDeactivationFails() {
        ColorDto color = service.createColor("Z-Rollback");
        ColorId colorId = ColorId.of(color.id());
        mappingRepository.insert(
                So7ColorMapping.create("SO7 Z-Rollback", colorId, colorId, ACTOR, CLOCK));

        So7ColorMappingRepository failingMappings =
                new So7ColorMappingRepository() {
                    @Override
                    public So7ColorMapping insert(So7ColorMapping mapping) {
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
                    public Optional<So7ColorMapping> findBySourceKey(
                            com.tmp.reference.domain.So7SourceKey sourceKey) {
                        return mappingRepository.findBySourceKey(sourceKey);
                    }

                    @Override
                    public List<So7ColorMapping> search(Boolean activeFilter, String searchText) {
                        return mappingRepository.search(activeFilter, searchText);
                    }

                    @Override
                    public int deactivateByColorId(
                            ColorId id, UUID updatedBy, Instant updatedAt) {
                        throw new IllegalStateException("forced deactivation failure");
                    }

                    @Override
                    public long countActiveByColorId(ColorId id) {
                        return mappingRepository.countActiveByColorId(id);
                    }

                    @Override
                    public void delete(UUID id, long expectedVersion) {
                        mappingRepository.delete(id, expectedVersion);
                    }
                };

        DefaultColorCatalogService failingService =
                new DefaultColorCatalogService(
                        authorization,
                        authentication,
                        colorRepository,
                        failingMappings,
                        new DataSourceTransactionManager(dataSource),
                        CLOCK);

        assertThrows(
                IllegalStateException.class,
                () -> failingService.archiveColor(color.id(), color.version()));

        Color stillActive = colorRepository.findById(colorId).orElseThrow();
        assertTrue(stillActive.active());
        assertEquals(0L, stillActive.version());
        assertEquals(1L, mappingRepository.countActiveByColorId(colorId));
    }

    @Test
    void archiveOptimisticLockConflictLeavesStateUnchanged() {
        ColorDto color = service.createColor("Z-OptLock");
        ColorId colorId = ColorId.of(color.id());
        mappingRepository.insert(
                So7ColorMapping.create("SO7 Z-OptLock", colorId, colorId, ACTOR, CLOCK));

        assertThrows(
                Color.OptimisticLockConflictException.class,
                () -> service.archiveColor(color.id(), 99L));

        assertTrue(colorRepository.findById(colorId).orElseThrow().active());
        assertEquals(1L, mappingRepository.countActiveByColorId(colorId));
    }

    @Test
    void seedDataUnchangedAndNoPhysicalColorDeletionPath() {
        assertEquals(
                860,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM reference.colors WHERE created_by IS NULL",
                        Integer.class));
        assertEquals(
                245,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM reference.so7_color_mappings WHERE created_by IS NULL",
                        Integer.class));

        ColorDto created = service.createColor("Z-NoDelete");
        service.archiveColor(created.id(), 0L);
        assertTrue(
                colorRepository.findById(ColorId.of(created.id())).isPresent(),
                "archived color row must remain physically present");
        assertEquals(
                861,
                jdbc.queryForObject("SELECT COUNT(*) FROM reference.colors", Integer.class));
    }
}
