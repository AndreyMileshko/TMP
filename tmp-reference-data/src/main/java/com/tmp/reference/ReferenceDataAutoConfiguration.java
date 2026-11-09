package com.tmp.reference;

import com.tmp.reference.api.ColorCatalogCommandApi;
import com.tmp.reference.api.ColorCatalogQueryApi;
import com.tmp.reference.api.ColorImportResolutionApi;
import com.tmp.reference.api.ColorMatchingSettingApi;
import com.tmp.reference.api.So7ColorMappingCommandApi;
import com.tmp.reference.api.So7ColorMappingQueryApi;
import com.tmp.reference.application.DefaultColorCatalogService;
import com.tmp.reference.application.DefaultColorImportResolutionService;
import com.tmp.reference.application.DefaultColorMatchingSettingService;
import com.tmp.reference.application.DefaultSo7ColorMappingService;
import com.tmp.reference.capability.ReferenceDataCapability;
import com.tmp.reference.domain.ColorMatchingSettingRepository;
import com.tmp.reference.domain.ColorRepository;
import com.tmp.reference.domain.So7ColorMappingRepository;
import com.tmp.reference.persistence.JdbcColorMatchingSettingRepository;
import com.tmp.reference.persistence.JdbcColorRepository;
import com.tmp.reference.persistence.JdbcSo7ColorMappingRepository;
import com.tmp.reference.security.ReferenceImportAccessPolicy;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Registers the Reference Data Capability, persistence adapters and public application APIs.
 *
 * <p>COL-020B: color catalog query/command. COL-021B: SO7 mapping query/command including {@code
 * saveMappingsFromImport}. COL-022A: automatic matching setting and import access policy.
 * COL-022B: import resolution API. COL-030A: navigation contribution; Capability bean uses
 * {@code @Lazy} public-service collaborators (same cycle break as ProductionCapability).
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
    @ConditionalOnBean(JdbcTemplate.class)
    ColorMatchingSettingRepository colorMatchingSettingRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcColorMatchingSettingRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnBean(AuthorizationService.class)
    ReferenceImportAccessPolicy referenceImportAccessPolicy(AuthorizationService authorizationService) {
        return new ReferenceImportAccessPolicy(authorizationService);
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
    @ConditionalOnBean({
        AuthorizationService.class,
        AuthenticationService.class,
        ReferenceImportAccessPolicy.class,
        ColorMatchingSettingRepository.class
    })
    DefaultColorMatchingSettingService defaultColorMatchingSettingService(
            AuthorizationService authorizationService,
            AuthenticationService authenticationService,
            ReferenceImportAccessPolicy referenceImportAccessPolicy,
            ColorMatchingSettingRepository colorMatchingSettingRepository,
            Clock clock) {
        return new DefaultColorMatchingSettingService(
                authorizationService,
                authenticationService,
                referenceImportAccessPolicy,
                colorMatchingSettingRepository,
                clock);
    }

    @Bean
    @ConditionalOnBean({
        ReferenceImportAccessPolicy.class,
        AuthorizationService.class,
        ColorMatchingSettingRepository.class,
        So7ColorMappingRepository.class,
        ColorRepository.class
    })
    DefaultColorImportResolutionService defaultColorImportResolutionService(
            ReferenceImportAccessPolicy referenceImportAccessPolicy,
            AuthorizationService authorizationService,
            ColorMatchingSettingRepository colorMatchingSettingRepository,
            So7ColorMappingRepository so7ColorMappingRepository,
            ColorRepository colorRepository) {
        return new DefaultColorImportResolutionService(
                referenceImportAccessPolicy,
                authorizationService,
                colorMatchingSettingRepository,
                so7ColorMappingRepository,
                colorRepository);
    }

    @Bean
    @ConditionalOnBean({
        DefaultColorCatalogService.class,
        DefaultSo7ColorMappingService.class,
        DefaultColorMatchingSettingService.class,
        DefaultColorImportResolutionService.class
    })
    ReferenceDataCapability referenceDataCapability(
            @Lazy ColorCatalogQueryApi colorCatalogQueryApi,
            @Lazy ColorCatalogCommandApi colorCatalogCommandApi,
            @Lazy So7ColorMappingQueryApi so7ColorMappingQueryApi,
            @Lazy So7ColorMappingCommandApi so7ColorMappingCommandApi,
            @Lazy ColorMatchingSettingApi colorMatchingSettingApi,
            @Lazy ColorImportResolutionApi colorImportResolutionApi) {
        return new ReferenceDataCapability(
                colorCatalogQueryApi,
                colorCatalogCommandApi,
                so7ColorMappingQueryApi,
                so7ColorMappingCommandApi,
                colorMatchingSettingApi,
                colorImportResolutionApi);
    }
}