package com.tmp.capability.sample;

import com.tmp.core.api.PlatformCore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Registers technical diagnostic sample capabilities when explicitly enabled. Not a business
 * workplace module — exists only for Stage 3 bootstrap visibility and integration tests.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "tmp.capability.sample.diagnostic", havingValue = "true")
public class SampleTechnicalCapabilitiesAutoConfiguration {

    @Bean
    SampleTechnicalCapability sampleTechnicalCapability() {
        return new SampleTechnicalCapability();
    }

    @Bean
    SampleDependentTechnicalCapability sampleDependentTechnicalCapability(PlatformCore platformCore) {
        return new SampleDependentTechnicalCapability(platformCore);
    }
}
