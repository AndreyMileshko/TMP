package com.tmp.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthenticationFailedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.SecurityPermissions;
import com.tmp.security.api.UserAdministrationService;
import com.tmp.security.domain.User;
import com.tmp.security.domain.repository.UserRepository;
import com.tmp.security.persistence.JdbcUserRepository;
import com.tmp.security.support.ControllableUserRepository;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Deterministic login vs logical-delete race: credentials may pass against a previously read
 * ACTIVE user, but session must not open once the user is DELETED.
 */
@Testcontainers
@SpringBootTest(classes = LoginDeleteRacePostgresIntegrationIT.TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "tmp.security.bootstrap.admin-login=admin",
            "tmp.security.bootstrap.admin-display-name=Administrator",
            "tmp.security.bootstrap.admin-password=bootstrap-secret-value"
        })
class LoginDeleteRacePostgresIntegrationIT {

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
    private ControllableUserRepository controllableUsers;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Clock clock;

    @BeforeEach
    void clearSessionAndBarrier() {
        controllableUsers.clearFindByIdBarrier();
        authenticationService.logout();
    }

    @Test
    void loginDoesNotOpenSessionWhenUserDeletedAfterCredentialCheck() throws Exception {
        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());
        var victim = userAdministrationService.createUser(
                Login.of("race-login-user"), DisplayName.of("Race Login"), "race-login-secret".toCharArray());
        authenticationService.logout();

        CountDownLatch enteredFindById = new CountDownLatch(1);
        CountDownLatch releaseFindById = new CountDownLatch(1);
        controllableUsers.armFindByIdBarrier(enteredFindById, releaseFindById);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<?> loginFuture = pool.submit(() -> authenticationService.login(
                Login.of("race-login-user"), "race-login-secret".toCharArray()));

        assertTrue(enteredFindById.await(30, TimeUnit.SECONDS));
        // Clear before any main-thread findById/save so we do not join the login barrier.
        controllableUsers.clearFindByIdBarrier();
        User current = userRepository.findById(victim.id()).orElseThrow();
        userRepository.save(current.deleted(clock));
        releaseFindById.countDown();

        assertThrows(AuthenticationFailedException.class, () -> {
            try {
                loginFuture.get(60, TimeUnit.SECONDS);
            } catch (Exception ex) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new RuntimeException(cause);
            }
        });
        assertFalse(authenticationService.isAuthenticated());
        assertThrows(
                AccessDeniedException.class,
                () -> authorizationService.requirePermission(SecurityPermissions.USERS_VIEW));
        pool.shutdownNow();
    }

    @SpringBootApplication
    @Import({
        com.tmp.infra.db.DatabaseAutoConfiguration.class,
        com.tmp.core.PlatformCoreAutoConfiguration.class,
        com.tmp.document.DocumentEngineAutoConfiguration.class,
        com.tmp.capability.CapabilityEngineAutoConfiguration.class,
        SecurityAutoConfiguration.class,
        UserProbeConfiguration.class
    })
    static class TestApplication {
    }

    static class UserProbeConfiguration {

        @Bean
        @Primary
        ControllableUserRepository controllableUserRepository(JdbcTemplate jdbcTemplate) {
            return new ControllableUserRepository(new JdbcUserRepository(jdbcTemplate));
        }
    }
}
