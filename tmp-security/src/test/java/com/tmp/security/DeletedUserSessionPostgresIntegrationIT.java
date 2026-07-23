package com.tmp.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AuthenticationFailedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.RoleAdministrationService;
import com.tmp.security.api.SecurityPermissions;
import com.tmp.security.api.UserAdministrationService;
import com.tmp.security.api.UserSummary;
import com.tmp.security.application.BootstrapAdministratorApplicationService;
import com.tmp.security.domain.User;
import com.tmp.security.domain.repository.RoleRepository;
import com.tmp.security.domain.repository.UserRepository;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = DeletedUserSessionPostgresIntegrationIT.TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "tmp.security.bootstrap.admin-login=admin",
            "tmp.security.bootstrap.admin-display-name=Administrator",
            "tmp.security.bootstrap.admin-password=bootstrap-secret-value"
        })
class DeletedUserSessionPostgresIntegrationIT {

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
    private AuthorizationService authorizationService;

    @Autowired
    private UserAdministrationService userAdministrationService;

    @Autowired
    private RoleAdministrationService roleAdministrationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private Clock clock;

    @BeforeEach
    void clearSession() {
        authenticationService.logout();
    }

    @Test
    void logicallyDeletedUserWithOpenSessionCannotAuthorize() {
        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());
        UserSummary victim = userAdministrationService.createUser(
                Login.of("deleted-session-user"),
                DisplayName.of("Deleted Session"),
                "victim-secret".toCharArray());
        var adminRole = roleRepository.findAll().stream()
                .filter(role -> BootstrapAdministratorApplicationService.SECURITY_ADMINISTRATOR_ROLE_NAME
                        .equals(role.name()))
                .findFirst()
                .orElseThrow();
        roleAdministrationService.assignRole(victim.id(), adminRole.id());
        authenticationService.logout();

        authenticationService.login(Login.of("deleted-session-user"), "victim-secret".toCharArray());
        assertTrue(authorizationService.hasPermission(SecurityPermissions.USERS_VIEW));
        assertTrue(authenticationService.isAuthenticated());

        User current = userRepository.findById(victim.id()).orElseThrow();
        userRepository.save(current.deleted(clock));

        assertTrue(authenticationService.isAuthenticated(), "session may remain until cleared");
        assertFalse(authorizationService.hasPermission(SecurityPermissions.USERS_VIEW));
        assertThrows(
                AuthenticationFailedException.class,
                () -> authenticationService.login(Login.of("deleted-session-user"), "victim-secret".toCharArray()));
    }

    @Test
    void deletingAuthenticatedUserClearsSession() {
        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());
        UserSummary operator = userAdministrationService.createUser(
                Login.of("self-delete-user"),
                DisplayName.of("Self Delete"),
                "self-secret".toCharArray());
        var adminRole = roleRepository.findAll().stream()
                .filter(role -> BootstrapAdministratorApplicationService.SECURITY_ADMINISTRATOR_ROLE_NAME
                        .equals(role.name()))
                .findFirst()
                .orElseThrow();
        roleAdministrationService.assignRole(operator.id(), adminRole.id());
        authenticationService.logout();

        authenticationService.login(Login.of("self-delete-user"), "self-secret".toCharArray());
        assertTrue(authenticationService.isAuthenticated());
        userAdministrationService.deleteUser(operator.id());
        assertFalse(authenticationService.isAuthenticated());
        assertFalse(authorizationService.hasPermission(SecurityPermissions.USERS_VIEW));
    }

    @Test
    void concurrentAuthorizationSeesDelete() throws Exception {
        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());
        UserSummary victim = userAdministrationService.createUser(
                Login.of("race-user"), DisplayName.of("Race"), "race-secret".toCharArray());
        var adminRole = roleRepository.findAll().stream()
                .filter(role -> BootstrapAdministratorApplicationService.SECURITY_ADMINISTRATOR_ROLE_NAME
                        .equals(role.name()))
                .findFirst()
                .orElseThrow();
        roleAdministrationService.assignRole(victim.id(), adminRole.id());
        authenticationService.logout();
        authenticationService.login(Login.of("race-user"), "race-secret".toCharArray());
        assertTrue(authorizationService.hasPermission(SecurityPermissions.USERS_VIEW));

        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch deleted = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        var future = pool.submit(() -> {
            ready.await(30, TimeUnit.SECONDS);
            if (!deleted.await(30, TimeUnit.SECONDS)) {
                return false;
            }
            return !authorizationService.hasPermission(SecurityPermissions.USERS_VIEW);
        });

        ready.countDown();
        User current = userRepository.findById(victim.id()).orElseThrow();
        userRepository.save(current.deleted(clock));
        deleted.countDown();

        assertTrue(future.get(60, TimeUnit.SECONDS));
        assertFalse(authorizationService.hasPermission(SecurityPermissions.USERS_VIEW));
        pool.shutdownNow();
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
