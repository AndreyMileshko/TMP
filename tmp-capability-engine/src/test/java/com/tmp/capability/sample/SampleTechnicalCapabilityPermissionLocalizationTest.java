package com.tmp.capability.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tmp.capability.api.PermissionDescriptor;
import org.junit.jupiter.api.Test;

class SampleTechnicalCapabilityPermissionLocalizationTest {

    @Test
    void samplePermissionHasRussianDisplayNameAndUnchangedId() {
        SampleTechnicalCapability capability = new SampleTechnicalCapability();
        PermissionDescriptor permission = capability.descriptor().permissions().get(0);
        assertEquals("sample.technical.view", permission.permissionId());
        assertEquals("Просмотр тестовых технических данных", permission.displayName());
    }
}
