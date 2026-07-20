package com.tmp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.tmp.core.api.PlatformCore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = PlatformCoreAutoConfigurationTest.TestApplication.class)
class PlatformCoreAutoConfigurationTest {

    @Autowired
    private PlatformCore platformCore;

    @Test
    void wiresPlatformCoreBean() {
        assertNotNull(platformCore);
        assertEquals("TOP Manufacturing Platform", platformCore.status().platformName());
        assertNotNull(platformCore.eventBus());
        assertNotNull(platformCore.platformRegistry());
    }

    @SpringBootApplication
    static class TestApplication {
    }
}
