package com.tmp.reference.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tmp.reference.api.ColorMatchingSettingApi.ColorMatchingSettingDto;
import com.tmp.reference.domain.ColorMatchingSettingRepository.OptimisticLockConflictException;
import com.tmp.reference.persistence.JdbcColorMatchingSettingRepository;
import com.tmp.reference.security.ReferenceDataPermissions;
import com.tmp.reference.security.ReferenceImportAccessPolicy;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.Login;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.SessionId;
import com.tmp.security.api.SessionSummary;
import com.tmp.security.api.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
 * COL-022A IT: seed OFF, read via view / full import set, mutate via color-setting.edit, optimistic
 * lock — against Flyway V58+V59 in Testcontainers PostgreSQL.
 */
@Testcontainers
class DefaultColorMatchingSettingServiceIT {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-09T07:00:00Z"), ZoneOffset.UTC);
    private static final UUID ACTOR = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcColorMatchingSettingRepository settingRepository;

    private AuthorizationService authorization;
    private AuthenticationService authentication;
    private DefaultColorMatchingSettingService service;

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
        settingRepository = new JdbcColorMatchingSettingRepository(jdbc);
    }

    @BeforeEach
    void setUp() {
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
        authentication = mock(AuthenticationService.class);
        when(authentication.currentSession())
                .thenReturn(
                        Optional.of(
                                new SessionSummary(
                                        SessionId.of(UUID.randomUUID()),
                                        UserId.of(ACTOR),
                                        Login.of("setting-editor"),
                                        CLOCK.instant())));
        service =
                new DefaultColorMatchingSettingService(
                        authorization,
                        authentication,
                        new ReferenceImportAccessPolicy(authorization),
                        settingRepository,
                        CLOCK);
    }

    @Test
    void seedDefaultIsOff() {
        when(authorization.hasPermission(ReferenceDataPermissions.COLOR_VIEW)).thenReturn(true);

        ColorMatchingSettingDto dto = service.getAutomaticMatchingSetting();

        assertFalse(dto.enabled());
        assertEquals(0L, dto.version());
    }

    @Test
    void readAllowedWithColorView() {
        when(authorization.hasPermission(ReferenceDataPermissions.COLOR_VIEW)).thenReturn(true);

        ColorMatchingSettingDto dto = service.getAutomaticMatchingSetting();

        assertFalse(dto.enabled());
    }

    @Test
    void readAllowedWithFullImportPermissionSet() {
        when(authorization.hasPermission(ReferenceDataPermissions.COLOR_VIEW)).thenReturn(false);
        for (String id : ReferenceImportAccessPolicy.ORDER_IMPORT_PERMISSION_IDS) {
            when(authorization.hasPermission(PermissionId.of(id))).thenReturn(true);
        }

        ColorMatchingSettingDto dto = service.getAutomaticMatchingSetting();

        assertFalse(dto.enabled());
    }

    @Test
    void readDeniedWithOnlyOrderCreate() {
        when(authorization.hasPermission(ReferenceDataPermissions.COLOR_VIEW)).thenReturn(false);
        when(authorization.hasPermission(PermissionId.of("order.order.create"))).thenReturn(true);
        when(authorization.hasPermission(PermissionId.of("order.item.create"))).thenReturn(false);

        assertThrows(AccessDeniedException.class, service::getAutomaticMatchingSetting);
    }

    @Test
    void setDeniedWithoutColorSettingEdit() {
        doThrow(new AccessDeniedException("no setting edit"))
                .when(authorization)
                .requirePermission(ReferenceDataPermissions.COLOR_SETTING_EDIT);

        assertThrows(
                AccessDeniedException.class, () -> service.setAutomaticMatchingEnabled(true, 0L));
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
    void setEnablesMatchingAndIncrementsVersion() {
        ColorMatchingSettingDto dto = service.setAutomaticMatchingEnabled(true, 0L);

        assertTrue(dto.enabled());
        assertEquals(1L, dto.version());
        assertTrue(
                Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                """
                                SELECT automatic_matching_enabled
                                FROM reference.color_matching_settings WHERE id = 1
                                """,
                                Boolean.class)));
        assertEquals(
                ACTOR,
                jdbc.queryForObject(
                        """
                        SELECT updated_by FROM reference.color_matching_settings WHERE id = 1
                        """,
                        UUID.class));
    }

    @Test
    void setRejectsStaleVersion() {
        service.setAutomaticMatchingEnabled(true, 0L);

        OptimisticLockConflictException ex =
                assertThrows(
                        OptimisticLockConflictException.class,
                        () -> service.setAutomaticMatchingEnabled(false, 0L));
        assertTrue(ex.getMessage().contains("version conflict"));
        assertTrue(
                Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                """
                                SELECT automatic_matching_enabled
                                FROM reference.color_matching_settings WHERE id = 1
                                """,
                                Boolean.class)));
    }
}