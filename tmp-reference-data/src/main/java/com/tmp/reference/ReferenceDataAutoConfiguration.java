package com.tmp.reference;

import com.tmp.reference.capability.ReferenceDataCapability;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Registers the Reference Data Capability bean for Capability Engine discovery.
 *
 * <p>COL-010: descriptor only — no permissions, navigation, persistence adapters or public APIs.
 */
@AutoConfiguration
public class ReferenceDataAutoConfiguration {

    @Bean
    ReferenceDataCapability referenceDataCapability() {
        return new ReferenceDataCapability();
    }
}
