package com.tmp.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.api.CapabilityEngine;
import com.tmp.capability.api.CapabilityLifecycleState;
import com.tmp.capability.sample.SampleDependentTechnicalCapability;
import com.tmp.capability.sample.SampleTechnicalCapability;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:tmp_bootstrap_capability_lookup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password="
        })
class CapabilityEngineBeanLookupTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private CapabilityEngine capabilityEngine;

    @Test
    void applicationContextResolvesSingleCapabilityEngineBean() {
        CapabilityEngine resolved = applicationContext.getBean(CapabilityEngine.class);
        assertNotNull(resolved);
    }

    @Test
    void capabilityEngineBeanLookupIsUnambiguous() {
        String[] beanNames = applicationContext.getBeanNamesForType(CapabilityEngine.class);
        assertEquals(1, beanNames.length);
        CapabilityEngine byType = applicationContext.getBean(CapabilityEngine.class);
        CapabilityEngine byName = applicationContext.getBean(beanNames[0], CapabilityEngine.class);
        assertSame(byType, byName);
    }

    @Test
    void formatCapabilityStatusIncludesCountsAndSampleCapabilityStates() {
        String statusText = DesktopBootstrap.formatCapabilityStatus(capabilityEngine);

        assertTrue(statusText.contains("discovered=2"));
        assertTrue(statusText.contains("active=2"));
        assertTrue(statusText.contains(SampleTechnicalCapability.ID.value() + " state=" + CapabilityLifecycleState.ACTIVE));
        assertTrue(statusText.contains(
                SampleDependentTechnicalCapability.ID.value() + " state=" + CapabilityLifecycleState.ACTIVE));
    }
}
