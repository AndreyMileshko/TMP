package com.tmp.reference;

import com.tmp.reference.capability.ReferenceDataCapability;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Registers the Reference Data Capability bean for Capability Engine discovery.
 *
 * <p>COL-023: Capability descriptor includes the four {@code reference.*} permissions. Navigation,
 * persistence adapters and public APIs remain later COL tasks.
 */
@AutoConfiguration
public class ReferenceDataAutoConfiguration {

    @Bean
    ReferenceDataCapability referenceDataCapability() {
        return new ReferenceDataCapability();
    }
}
