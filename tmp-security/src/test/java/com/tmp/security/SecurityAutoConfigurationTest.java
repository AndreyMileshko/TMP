package com.tmp.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.api.CapabilityEngine;
import com.tmp.core.api.PlatformCore;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.AuditQueryService;
import com.tmp.security.api.RoleAdministrationService;
import com.tmp.security.api.UserAdministrationService;
import com.tmp.security.application.SessionContext;
import com.tmp.security.domain.PasswordHasher;
import com.tmp.security.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = SecurityAutoConfigurationTest.TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "tmp.security.bootstrap.admin-login=admin",
        "tmp.security.bootstrap.admin-display-name=Administrator",
        "tmp.security.bootstrap.admin-password=bootstrap-secret-value"
})
class SecurityAutoConfigurationTest {

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
    private ApplicationContext applicationContext;

    @Autowired
    private PlatformCore platformCore;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthenticationService authenticationService;

    @Test
    void exactlyOneBeanPerPublicContract() {
        assertEquals(1, applicationContext.getBeansOfType(AuthenticationService.class).size());
        assertEquals(1, applicationContext.getBeansOfType(AuthorizationService.class).size());
        assertEquals(1, applicationContext.getBeansOfType(UserAdministrationService.class).size());
        assertEquals(1, applicationContext.getBeansOfType(RoleAdministrationService.class).size());
        assertEquals(1, applicationContext.getBeansOfType(AuditQueryService.class).size());
        assertEquals(1, applicationContext.getBeansOfType(SessionContext.class).size());
        assertEquals(1, applicationContext.getBeansOfType(PasswordHasher.class).size());
        assertEquals(1, applicationContext.getBeansOfType(SecurityPlatformComponent.class).size());
        assertEquals(1, applicationContext.getBeansOfType(CapabilityEngine.class).size());
        assertNotNull(authenticationService);
    }

    @Test
    void securityInitializesAfterCapabilityEngineAndBootstrapsAdmin() {
        // PlatformCoreLifecycleListener already starts components on ApplicationReadyEvent.
        assertTrue(platformCore.platformRegistry().findById("capability-engine").isPresent());
        assertTrue(platformCore.platformRegistry().findById("security").isPresent());
        assertTrue(userRepository.existsAny());
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
