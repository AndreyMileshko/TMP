package com.tmp.security;

import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityEngine;
import com.tmp.core.api.PlatformCore;
import com.tmp.core.api.component.PlatformComponent;
import com.tmp.security.api.AuditQueryService;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.RoleAdministrationService;
import com.tmp.security.api.SecuredOperationDemo;
import com.tmp.security.api.UserAdministrationService;
import com.tmp.security.application.AuditQueryApplicationService;
import com.tmp.security.application.AuthenticationApplicationService;
import com.tmp.security.application.AuthorizationApplicationService;
import com.tmp.security.application.BootstrapAdministratorApplicationService;
import com.tmp.security.application.DefaultAuditQueryService;
import com.tmp.security.application.DefaultAuthenticationService;
import com.tmp.security.application.DefaultAuthorizationService;
import com.tmp.security.application.DefaultRoleAdministrationService;
import com.tmp.security.application.DefaultUserAdministrationService;
import com.tmp.security.application.PasswordApplicationService;
import com.tmp.security.application.PermissionOverrideApplicationService;
import com.tmp.security.application.PermissionSynchronizationApplicationService;
import com.tmp.security.application.RoleAdministrationApplicationService;
import com.tmp.security.application.RoleAssignmentApplicationService;
import com.tmp.security.application.SecurityBootstrapProperties;
import com.tmp.security.application.SessionContext;
import com.tmp.security.application.UserAdministrationApplicationService;
import com.tmp.security.capability.SecurityAdministrationCapability;
import com.tmp.security.domain.PasswordHasher;
import com.tmp.security.domain.repository.PermissionDefinitionRepository;
import com.tmp.security.domain.repository.PermissionOverrideRepository;
import com.tmp.security.domain.repository.RoleAssignmentRepository;
import com.tmp.security.domain.repository.RoleRepository;
import com.tmp.security.domain.repository.SecurityAuditRepository;
import com.tmp.security.domain.repository.UserRepository;
import com.tmp.security.infrastructure.BCryptPasswordHasher;
import com.tmp.security.persistence.JdbcPermissionDefinitionRepository;
import com.tmp.security.persistence.JdbcPermissionOverrideRepository;
import com.tmp.security.persistence.JdbcRoleAssignmentRepository;
import com.tmp.security.persistence.JdbcRoleRepository;
import com.tmp.security.persistence.JdbcSecurityAuditRepository;
import com.tmp.security.persistence.JdbcUserRepository;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfiguration
@AutoConfigureAfter(
        name = {
            "com.tmp.core.PlatformCoreAutoConfiguration",
            "com.tmp.infra.db.DatabaseAutoConfiguration",
            "com.tmp.capability.CapabilityEngineAutoConfiguration"
        })
@EnableConfigurationProperties(SecurityBootstrapProperties.class)
@EnableTransactionManagement
public class SecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock securityClock() {
        return Clock.systemUTC();
    }

    @Bean
    PasswordHasher passwordHasher() {
        return new BCryptPasswordHasher();
    }

    @Bean
    UserRepository userRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcUserRepository(jdbcTemplate);
    }

    @Bean
    RoleRepository roleRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRoleRepository(jdbcTemplate);
    }

    @Bean
    PermissionDefinitionRepository permissionDefinitionRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcPermissionDefinitionRepository(jdbcTemplate);
    }

    @Bean
    RoleAssignmentRepository roleAssignmentRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRoleAssignmentRepository(jdbcTemplate);
    }

    @Bean
    PermissionOverrideRepository permissionOverrideRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcPermissionOverrideRepository(jdbcTemplate);
    }

    @Bean
    SecurityAuditRepository securityAuditRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcSecurityAuditRepository(jdbcTemplate);
    }

    @Bean
    SessionContext sessionContext() {
        return new SessionContext();
    }

    @Bean
    Capability securityAdministrationCapability() {
        return new SecurityAdministrationCapability();
    }

    @Bean
    PermissionSynchronizationApplicationService permissionSynchronizationApplicationService(
            CapabilityEngine capabilityEngine,
            PermissionDefinitionRepository permissionDefinitionRepository,
            SecurityAuditRepository securityAuditRepository,
            Clock securityClock) {
        return new PermissionSynchronizationApplicationService(
                capabilityEngine, permissionDefinitionRepository, securityAuditRepository, securityClock);
    }

    @Bean
    BootstrapAdministratorApplicationService bootstrapAdministratorApplicationService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            SecurityAuditRepository securityAuditRepository,
            PasswordHasher passwordHasher,
            SecurityBootstrapProperties securityBootstrapProperties,
            Clock securityClock,
            JdbcTemplate jdbcTemplate) {
        return new BootstrapAdministratorApplicationService(
                userRepository,
                roleRepository,
                roleAssignmentRepository,
                securityAuditRepository,
                passwordHasher,
                securityBootstrapProperties,
                securityClock,
                jdbcTemplate);
    }

    @Bean
    TransactionTemplate securityAuthenticationTransactionTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    @Bean
    AuthenticationApplicationService authenticationApplicationService(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            SessionContext sessionContext,
            SecurityAuditRepository securityAuditRepository,
            Clock securityClock,
            TransactionTemplate securityAuthenticationTransactionTemplate) {
        return new AuthenticationApplicationService(
                userRepository,
                passwordHasher,
                sessionContext,
                securityAuditRepository,
                securityClock,
                securityAuthenticationTransactionTemplate);
    }

    @Bean
    AuthorizationApplicationService authorizationApplicationService(
            SessionContext sessionContext,
            UserRepository userRepository,
            CapabilityEngine capabilityEngine,
            RoleAssignmentRepository roleAssignmentRepository,
            RoleRepository roleRepository,
            PermissionOverrideRepository permissionOverrideRepository) {
        return new AuthorizationApplicationService(
                sessionContext,
                userRepository,
                capabilityEngine,
                roleAssignmentRepository,
                roleRepository,
                permissionOverrideRepository);
    }

    @Bean
    UserAdministrationApplicationService userAdministrationApplicationService(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            AuthorizationApplicationService authorizationApplicationService,
            SecurityAuditRepository securityAuditRepository,
            SessionContext sessionContext,
            Clock securityClock) {
        return new UserAdministrationApplicationService(
                userRepository,
                passwordHasher,
                authorizationApplicationService,
                securityAuditRepository,
                sessionContext,
                securityClock);
    }

    @Bean
    PasswordApplicationService passwordApplicationService(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            AuthorizationApplicationService authorizationApplicationService,
            SecurityAuditRepository securityAuditRepository,
            SessionContext sessionContext,
            Clock securityClock) {
        return new PasswordApplicationService(
                userRepository,
                passwordHasher,
                authorizationApplicationService,
                securityAuditRepository,
                sessionContext,
                securityClock);
    }

    @Bean
    RoleAdministrationApplicationService roleAdministrationApplicationService(
            RoleRepository roleRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            AuthorizationApplicationService authorizationApplicationService,
            SecurityAuditRepository securityAuditRepository,
            SessionContext sessionContext,
            Clock securityClock) {
        return new RoleAdministrationApplicationService(
                roleRepository,
                roleAssignmentRepository,
                authorizationApplicationService,
                securityAuditRepository,
                sessionContext,
                securityClock);
    }

    @Bean
    RoleAssignmentApplicationService roleAssignmentApplicationService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            AuthorizationApplicationService authorizationApplicationService,
            SecurityAuditRepository securityAuditRepository,
            SessionContext sessionContext,
            Clock securityClock) {
        return new RoleAssignmentApplicationService(
                userRepository,
                roleRepository,
                roleAssignmentRepository,
                authorizationApplicationService,
                securityAuditRepository,
                sessionContext,
                securityClock);
    }

    @Bean
    PermissionOverrideApplicationService permissionOverrideApplicationService(
            UserRepository userRepository,
            PermissionOverrideRepository permissionOverrideRepository,
            AuthorizationApplicationService authorizationApplicationService,
            SecurityAuditRepository securityAuditRepository,
            SessionContext sessionContext,
            Clock securityClock) {
        return new PermissionOverrideApplicationService(
                userRepository,
                permissionOverrideRepository,
                authorizationApplicationService,
                securityAuditRepository,
                sessionContext,
                securityClock);
    }

    @Bean
    AuditQueryApplicationService auditQueryApplicationService(
            SecurityAuditRepository securityAuditRepository,
            AuthorizationApplicationService authorizationApplicationService) {
        return new AuditQueryApplicationService(securityAuditRepository, authorizationApplicationService);
    }

    @Bean
    AuthenticationService authenticationService(AuthenticationApplicationService authenticationApplicationService) {
        return new DefaultAuthenticationService(authenticationApplicationService);
    }

    @Bean
    AuthorizationService authorizationService(AuthorizationApplicationService authorizationApplicationService) {
        return new DefaultAuthorizationService(authorizationApplicationService);
    }

    @Bean
    UserAdministrationService userAdministrationService(
            UserAdministrationApplicationService userAdministrationApplicationService,
            PasswordApplicationService passwordApplicationService) {
        return new DefaultUserAdministrationService(
                userAdministrationApplicationService, passwordApplicationService);
    }

    @Bean
    RoleAdministrationService roleAdministrationService(
            RoleAdministrationApplicationService roleAdministrationApplicationService,
            RoleAssignmentApplicationService roleAssignmentApplicationService,
            PermissionOverrideApplicationService permissionOverrideApplicationService,
            PermissionDefinitionRepository permissionDefinitionRepository) {
        return new DefaultRoleAdministrationService(
                roleAdministrationApplicationService,
                roleAssignmentApplicationService,
                permissionOverrideApplicationService,
                permissionDefinitionRepository);
    }

    @Bean
    AuditQueryService auditQueryService(AuditQueryApplicationService auditQueryApplicationService) {
        return new DefaultAuditQueryService(auditQueryApplicationService);
    }

    @Bean
    SecuredOperationDemo securedOperationDemo(AuthorizationService authorizationService) {
        return new SecuredOperationDemo(authorizationService);
    }

    @Bean
    SecurityPlatformComponent securityPlatformComponent(
            PermissionSynchronizationApplicationService permissionSynchronizationApplicationService,
            BootstrapAdministratorApplicationService bootstrapAdministratorApplicationService) {
        return new SecurityPlatformComponent(
                permissionSynchronizationApplicationService, bootstrapAdministratorApplicationService);
    }

    @Bean
    SecurityPlatformRegistrar securityPlatformRegistrar(
            PlatformCore platformCore, SecurityPlatformComponent securityPlatformComponent) {
        return new SecurityPlatformRegistrar(platformCore, securityPlatformComponent);
    }

    static final class SecurityPlatformRegistrar {

        private final PlatformCore platformCore;
        private final PlatformComponent securityComponent;

        SecurityPlatformRegistrar(PlatformCore platformCore, PlatformComponent securityComponent) {
            this.platformCore = platformCore;
            this.securityComponent = securityComponent;
        }

        @PostConstruct
        void registerSecurityComponent() {
            platformCore.registerComponent(securityComponent);
        }
    }
}
