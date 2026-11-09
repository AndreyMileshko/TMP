package com.tmp.reference.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.reference.security.ReferenceDataPermissionCatalog;
import org.junit.jupiter.api.Test;

class ReferenceDataCapabilityTest {

    @Test
    void descriptorExposesReferenceCapabilityWithPermissionsWithoutNavigation() {
        ReferenceDataCapability capability = new ReferenceDataCapability();

        assertEquals(ReferenceDataCapability.ID, capability.descriptor().id());
        assertEquals("Reference Data", capability.descriptor().name());
        assertEquals(ReferenceDataCapability.VERSION, capability.descriptor().version());
        assertEquals(
                ReferenceDataPermissionCatalog.all(), capability.descriptor().permissions());
        assertTrue(capability.descriptor().commands().isEmpty());
        assertTrue(capability.descriptor().views().isEmpty());
        assertTrue(capability.descriptor().navigationContributions().isEmpty());
        assertTrue(capability.descriptor().publicServices().isEmpty());
    }
}
