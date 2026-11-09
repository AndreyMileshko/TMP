package com.tmp.reference;

import com.tmp.reference.application.DefaultColorCatalogService;
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
 * Registers the Reference Data Capability, persistence adapters and color catalog public APIs.
 *
 * <p>COL-020B: {@code ColorCatalogQueryApi} / {@code ColorCatalogCommandApi} via {@link
 * DefaultColorCatalogService}.
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
    @ConditionalOnBean(DefaultColorCatalogService.class)
    ReferenceDataCapability referenceDataCapability(DefaultColorCatalogService colorCatalogService) {
        return new ReferenceDataCapability(colorCatalogService, colorCatalogService);
    }
}
