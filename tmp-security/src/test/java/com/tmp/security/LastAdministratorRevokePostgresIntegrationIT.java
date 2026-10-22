package com.tmp.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.DisplayName;
import com.tmp.security.api.LastAdministratorException;
import com.tmp.security.api.Login;
import com.tmp.security.api.RoleAdministrationService;
import com.tmp.security.api.RoleId;
import com.tmp.security.api.SecurityPermissions;
import com.tmp.security.api.UserAdministrationService;
import com.tmp.security.api.UserId;
import com.tmp.security.api.UserSummary;
import com.tmp.security.application.BootstrapAdministratorApplicationService;
import com.tmp.security.application.LastAdministratorGuard;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Last-administrator lockout guard across role revoke, user deletion and role permission
 * updates, including concurrent cross-operation races.
 */
@Testcontainers
@SpringBootTest(classes = LastAdministratorRevokePostgresIntegrationIT.TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "tmp.security.bootstrap.admin-login=admin",
            "tmp.security.bootstrap.admin-display-name=Administrator",
            "tmp.security.bootstrap.admin-password=bootstrap-secret-value"
        })
class LastAdministratorRevokePostgresIntegrationIT {

    private static final char[] ADMIN_PASSWORD = "bootstrap-secret-value".toCharArray();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private UserAdministrationService userAdministrationService;

    @Autowired
    private RoleAdministrationService roleAdministrationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearSession() {
        authenticationService.logout();
    }

    @Test
    void lastActiveAdministratorCannotRevokeOwnAdministratorAssignment() {
        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());
        UserId adminId = authenticationService.currentSession().orElseThrow().userId();
        RoleId adminRoleId = ensureSoleBootstrapAdministrator(adminId);

        LastAdministratorException ex = assertThrows(
                LastAdministratorException.class,
                () -> roleAdministrationService.revokeRole(adminId, adminRoleId));
        assertEquals(LastAdministratorGuard.ROLE_REVOKE_MESSAGE, ex.getMessage());
        assertEquals(1L, countUsersForRole(adminRoleId));
        assertEquals(1L, countUserRoleRows(adminId, adminRoleId));
    }

    @Test
    void rejectsDeletionOfLastEligibleAdministrator() {
        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());
        UserId adminId = authenticationService.currentSession().orElseThrow().userId();
        RoleId adminRoleId = ensureSoleBootstrapAdministrator(adminId);

        LastAdministratorException ex = assertThrows(
                LastAdministratorException.class, () -> userAdministrationService.deleteUser(adminId));
        assertEquals(LastAdministratorGuard.USER_DELETE_MESSAGE, ex.getMessage());
        assertEquals("ACTIVE", userStatus(adminId));
        assertEquals(1L, countUserRoleRows(adminId, adminRoleId));
    }

    @Test
    void rejectsPermissionUpdateRemovingLastRolesAssign() {
        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());
        UserId adminId = authenticationService.currentSession().orElseThrow().userId();
        RoleId adminRoleId = ensureSoleBootstrapAdministrator(adminId);
        Set<String> before = rolePermissionIds(adminRoleId);

        LastAdministratorException ex = assertThrows(
                LastAdministratorException.class,
                () -> roleAdministrationService.setRolePermissions(
                        adminRoleId, Set.of(SecurityPermissions.USERS_VIEW)));
        assertEquals(LastAdministratorGuard.PERMISSION_REMOVE_MESSAGE, ex.getMessage());
        assertEquals(before, rolePermissionIds(adminRoleId));
    }

    @Test
    void concurrentRevokesCannotLeaveZeroEligibleAdministrators() throws Exception {
        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());
        UserId firstAdminId = authenticationService.currentSession().orElseThrow().userId();
        RoleId adminRoleId = ensureSoleBootstrapAdministrator(firstAdminId);

        var secondCreation = userAdministrationService.createUser(
                Login.of("second-admin-" + System.nanoTime()), DisplayName.of("Second Admin"));
        UserSummary secondAdmin = secondCreation.user();
        roleAdministrationService.assignRole(secondAdmin.id(), adminRoleId);
        assertEquals(2L, countUsersForRole(adminRoleId));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejections = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> {
                ready.countDown();
                go.await(10, TimeUnit.SECONDS);
                try {
                    roleAdministrationService.revokeRole(firstAdminId, adminRoleId);
                    successes.incrementAndGet();
                } catch (LastAdministratorException ex) {
                    rejections.incrementAndGet();
                }
                return null;
            });
            Future<?> second = pool.submit(() -> {
                ready.countDown();
                go.await(10, TimeUnit.SECONDS);
                try {
                    roleAdministrationService.revokeRole(secondAdmin.id(), adminRoleId);
                    successes.incrementAndGet();
                } catch (LastAdministratorException ex) {
                    rejections.incrementAndGet();
                }
                return null;
            });
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, successes.get(), "exactly one concurrent revoke should succeed");
        assertEquals(1, rejections.get(), "exactly one concurrent revoke should be rejected");
        assertEquals(1L, countUsersForRole(adminRoleId), "one eligible administrator must remain");
    }

    @Test
    void concurrentRevokeAndDeletionCannotLeaveZeroEligibleAdministrators() throws Exception {
        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());
        UserId firstAdminId = authenticationService.currentSession().orElseThrow().userId();
        RoleId adminRoleId = ensureSoleBootstrapAdministrator(firstAdminId);

        var secondCreation = userAdministrationService.createUser(
                Login.of("cross-admin-" + System.nanoTime()), DisplayName.of("Cross Admin"));
        UserSummary secondAdmin = secondCreation.user();
        roleAdministrationService.assignRole(secondAdmin.id(), adminRoleId);
        assertEquals(2L, countUsersForRole(adminRoleId));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejections = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> revoke = pool.submit(() -> {
                ready.countDown();
                go.await(10, TimeUnit.SECONDS);
                try {
                    roleAdministrationService.revokeRole(firstAdminId, adminRoleId);
                    successes.incrementAndGet();
                } catch (LastAdministratorException ex) {
                    rejections.incrementAndGet();
                }
                return null;
            });
            Future<?> delete = pool.submit(() -> {
                ready.countDown();
                go.await(10, TimeUnit.SECONDS);
                try {
                    userAdministrationService.deleteUser(secondAdmin.id());
                    successes.incrementAndGet();
                } catch (LastAdministratorException ex) {
                    rejections.incrementAndGet();
                }
                return null;
            });
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            revoke.get(30, TimeUnit.SECONDS);
            delete.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, successes.get());
        assertEquals(1, rejections.get());
        assertTrue(countActiveRoleBasedEligibleAdministrators(adminRoleId) >= 1L);
    }

    @Test
    void concurrentDeletionAndPermissionRemovalCannotLeaveZeroEligibleAdministrators() throws Exception {
        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());
        UserId firstAdminId = authenticationService.currentSession().orElseThrow().userId();
        RoleId adminRoleId = ensureSoleBootstrapAdministrator(firstAdminId);

        var alternateRole = roleAdministrationService.createRole(
                "AltAdmin-" + System.nanoTime(), "Alternate administrator role");
        RoleId alternateRoleId = alternateRole.id();
        roleAdministrationService.setRolePermissions(
                alternateRoleId, Set.of(SecurityPermissions.ROLES_ASSIGN, SecurityPermissions.USERS_VIEW));

        var secondCreation = userAdministrationService.createUser(
                Login.of("perm-admin-" + System.nanoTime()), DisplayName.of("Perm Admin"));
        UserSummary secondAdmin = secondCreation.user();
        roleAdministrationService.assignRole(secondAdmin.id(), alternateRoleId);

        // Each mutation is allowed alone: delete second OR strip first role's assign.
        // Concurrently both would leave zero eligible administrators.
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejections = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> delete = pool.submit(() -> {
                ready.countDown();
                go.await(10, TimeUnit.SECONDS);
                try {
                    userAdministrationService.deleteUser(secondAdmin.id());
                    successes.incrementAndGet();
                } catch (LastAdministratorException ex) {
                    rejections.incrementAndGet();
                }
                return null;
            });
            Future<?> stripPermissions = pool.submit(() -> {
                ready.countDown();
                go.await(10, TimeUnit.SECONDS);
                try {
                    roleAdministrationService.setRolePermissions(
                            adminRoleId, Set.of(SecurityPermissions.USERS_VIEW));
                    successes.incrementAndGet();
                } catch (LastAdministratorException ex) {
                    rejections.incrementAndGet();
                }
                return null;
            });
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            delete.get(30, TimeUnit.SECONDS);
            stripPermissions.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, successes.get());
        assertEquals(1, rejections.get());
        assertTrue(
                countActiveEligibleAdministrators() >= 1L,
                "at least one ACTIVE eligible administrator must remain");
    }

    private long countActiveEligibleAdministrators() {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT u.id)
                FROM security.users u
                WHERE u.status = 'ACTIVE'
                  AND (
                    EXISTS (
                      SELECT 1
                      FROM security.user_roles ur
                      JOIN security.role_permissions rp ON rp.role_id = ur.role_id
                      WHERE ur.user_id = u.id
                        AND rp.permission_id = ?
                    )
                    OR EXISTS (
                      SELECT 1
                      FROM security.user_permission_overrides po
                      WHERE po.user_id = u.id
                        AND po.permission_id = ?
                        AND po.decision = 'GRANT'
                    )
                  )
                  AND NOT EXISTS (
                    SELECT 1
                    FROM security.user_permission_overrides po
                    WHERE po.user_id = u.id
                      AND po.permission_id = ?
                      AND po.decision = 'REVOKE'
                  )
                """,
                Long.class,
                SecurityPermissions.ROLES_ASSIGN.value(),
                SecurityPermissions.ROLES_ASSIGN.value(),
                SecurityPermissions.ROLES_ASSIGN.value());
        return count == null ? 0L : count;
    }

    private long countActiveRoleBasedEligibleAdministrators(RoleId adminRoleId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT u.id)
                FROM security.users u
                JOIN security.user_roles ur ON ur.user_id = u.id
                JOIN security.role_permissions rp ON rp.role_id = ur.role_id
                WHERE u.status = 'ACTIVE'
                  AND ur.role_id = ?::uuid
                  AND rp.permission_id = ?
                """,
                Long.class,
                adminRoleId.value(),
                SecurityPermissions.ROLES_ASSIGN.value());
        return count == null ? 0L : count;
    }

    private RoleId ensureSoleBootstrapAdministrator(UserId bootstrapAdminId) {
        RoleId adminRoleId = securityAdministratorRoleId();
        // Restore canonical Security Administrator permissions — prior tests may have stripped them.
        jdbcTemplate.update("DELETE FROM security.role_permissions WHERE role_id = ?::uuid", adminRoleId.value());
        for (String permissionId : SECURITY_ADMINISTRATOR_PERMISSIONS) {
            jdbcTemplate.update(
                    """
                    INSERT INTO security.role_permissions (role_id, permission_id, granted_at)
                    VALUES (?::uuid, ?, now())
                    """,
                    adminRoleId.value(),
                    permissionId);
        }
        jdbcTemplate.update("DELETE FROM security.user_roles WHERE role_id = ?::uuid", adminRoleId.value());
        jdbcTemplate.update(
                """
                INSERT INTO security.user_roles (user_id, role_id, assigned_at)
                VALUES (?::uuid, ?::uuid, now())
                """,
                bootstrapAdminId.value(),
                adminRoleId.value());
        assertEquals(1L, countUsersForRole(adminRoleId));
        assertTrue(rolePermissionIds(adminRoleId).contains(SecurityPermissions.ROLES_ASSIGN.value()));
        return adminRoleId;
    }

    private static final Set<String> SECURITY_ADMINISTRATOR_PERMISSIONS = Set.of(
            SecurityPermissions.USERS_VIEW.value(),
            SecurityPermissions.USERS_CREATE.value(),
            SecurityPermissions.USERS_UPDATE.value(),
            SecurityPermissions.USERS_DELETE.value(),
            SecurityPermissions.USERS_RESET_PASSWORD.value(),
            SecurityPermissions.ROLES_VIEW.value(),
            SecurityPermissions.ROLES_CREATE.value(),
            SecurityPermissions.ROLES_UPDATE.value(),
            SecurityPermissions.ROLES_DELETE.value(),
            SecurityPermissions.ROLES_ASSIGN.value(),
            SecurityPermissions.PERMISSIONS_ASSIGN.value(),
            SecurityPermissions.AUDIT_VIEW.value());

    private RoleId securityAdministratorRoleId() {
        java.util.UUID id = jdbcTemplate.queryForObject(
                "SELECT id FROM security.roles WHERE name = ?",
                java.util.UUID.class,
                BootstrapAdministratorApplicationService.SECURITY_ADMINISTRATOR_ROLE_NAME);
        if (id == null) {
            throw new IllegalStateException("Security Administrator role missing");
        }
        return RoleId.of(id);
    }

    private long countUsersForRole(RoleId roleId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM security.user_roles WHERE role_id = ?::uuid",
                Long.class,
                roleId.value());
        return count == null ? 0L : count;
    }

    private long countUserRoleRows(UserId userId, RoleId roleId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM security.user_roles
                WHERE user_id = ?::uuid AND role_id = ?::uuid
                """,
                Long.class,
                userId.value(),
                roleId.value());
        return count == null ? 0L : count;
    }

    private String userStatus(UserId userId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM security.users WHERE id = ?::uuid",
                String.class,
                userId.value());
    }

    private Set<String> rolePermissionIds(RoleId roleId) {
        List<String> ids = jdbcTemplate.query(
                "SELECT permission_id FROM security.role_permissions WHERE role_id = ?::uuid",
                (rs, rowNum) -> rs.getString(1),
                roleId.value());
        return new HashSet<>(ids);
    }

    @SpringBootApplication
    @Import({
        com.tmp.infra.db.DatabaseAutoConfiguration.class,
        com.tmp.core.PlatformCoreAutoConfiguration.class,
        com.tmp.document.DocumentEngineAutoConfiguration.class,
        com.tmp.capability.CapabilityEngineAutoConfiguration.class,
        SecurityAutoConfiguration.class
    })
    static class TestApplication {
    }
}