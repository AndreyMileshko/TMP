package com.tmp.reference;

import com.tmp.reference.capability.ReferenceDataCapability;
import com.tmp.reference.domain.ColorRepository;
import com.tmp.reference.persistence.JdbcColorRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Registers the Reference Data Capability and persistence adapters.
 *
 * <p>COL-020A: {@link ColorRepository} over {@code reference.colors}. Public catalog APIs remain
 * later COL tasks.
 */
@AutoConfiguration
@AutoConfigureAfter(JdbcTemplateAutoConfiguration.class)
public class ReferenceDataAutoConfiguration {

    @Bean
    ReferenceDataCapability referenceDataCapability() {
        return new ReferenceDataCapability();
    }

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    ColorRepository colorRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcColorRepository(jdbcTemplate);
    }
}