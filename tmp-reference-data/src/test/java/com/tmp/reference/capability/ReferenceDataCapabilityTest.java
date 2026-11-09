package com.tmp.reference.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReferenceDataCapabilityTest {

    @Test
    void descriptorExposesReferenceCapabilityWithoutPermissionsOrNavigation() {
        ReferenceDataCapability capability = new ReferenceDataCapability();

        assertEquals(ReferenceDataCapability.ID, capability.descriptor().id());
        assertEquals("Reference Data", capability.descriptor().name());
        assertEquals(ReferenceDataCapability.VERSION, capability.descriptor().version());
        assertTrue(capability.descriptor().permissions().isEmpty());
        assertTrue(capability.descriptor().commands().isEmpty());
        assertTrue(capability.descriptor().views().isEmpty());
        assertTrue(capability.descriptor().navigationContributions().isEmpty());
        assertTrue(capability.descriptor().publicServices().isEmpty());
    }
}
