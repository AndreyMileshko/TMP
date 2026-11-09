package com.tmp.reference;

import com.tmp.reference.application.DefaultColorCatalogService;
import com.tmp.reference.application.DefaultSo7ColorMappingService;
import com.tmp.reference.capability.ReferenceDataCapability;
import com.tmp.reference.domain.ColorRepository;
import com.tmp.reference.domain.So7ColorMappingRepository;
import com.tmp.reference.persistence.JdbcColorRepository;
import com.tmp.reference.persistence.JdbcSo7ColorMappingRepository;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Registers the Reference Data Capability, persistence adapters and public application APIs.
 *
 * <p>COL-020B: color catalog query/command. COL-021B: SO7 mapping query/command including {@code
 * saveMappingsFromImport}.
 */
@AutoConfiguration
@AutoConfigureAfter(JdbcTemplateAutoConfiguration.class)
public class ReferenceDataAutoConfiguration {

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    ColorRepository colorRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcColorRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    So7ColorMappingRepository so7ColorMappingRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcSo7ColorMappingRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnBean({
        AuthorizationService.class,
        AuthenticationService.class,
        ColorRepository.class,
        So7ColorMappingRepository.class,
        PlatformTransactionManager.class
    })
    DefaultColorCatalogService defaultColorCatalogService(
            AuthorizationService authorizationService,
            AuthenticationService authenticationService,
            ColorRepository colorRepository,
            So7ColorMappingRepository so7ColorMappingRepository,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        return new DefaultColorCatalogService(
                authorizationService,
                authenticationService,
                colorRepository,
                so7ColorMappingRepository,
                transactionManager,
                clock);
    }

    @Bean
    @ConditionalOnBean({
        AuthorizationService.class,
        AuthenticationService.class,
        ColorRepository.class,
        So7ColorMappingRepository.class,
        PlatformTransactionManager.class
    })
    DefaultSo7ColorMappingService defaultSo7ColorMappingService(
            AuthorizationService authorizationService,
            AuthenticationService authenticationService,
            ColorRepository colorRepository,
            So7ColorMappingRepository so7ColorMappingRepository,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        return new DefaultSo7ColorMappingService(
                authorizationService,
                authenticationService,
                colorRepository,
                so7ColorMappingRepository,
                transactionManager,
                clock);
    }

    @Bean
    @ConditionalOnBean({DefaultColorCatalogService.class, DefaultSo7ColorMappingService.class})
    ReferenceDataCapability referenceDataCapability(
            DefaultColorCatalogService colorCatalogService,
            DefaultSo7ColorMappingService so7ColorMappingService) {
        return new ReferenceDataCapability(
                colorCatalogService,
                colorCatalogService,
                so7ColorMappingService,
                so7ColorMappingService);
    }
}