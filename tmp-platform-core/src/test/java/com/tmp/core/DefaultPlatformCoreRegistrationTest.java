package com.tmp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.core.api.PlatformConfiguration;
import com.tmp.core.api.component.ComponentLifecycleState;
import com.tmp.core.api.component.ComponentType;
import com.tmp.core.api.component.PlatformComponentMetadata;
import com.tmp.core.event.SynchronousEventBus;
import com.tmp.core.lifecycle.DefaultLifecycleManager;
import com.tmp.core.registry.DefaultCapabilityRegistry;
import com.tmp.core.registry.DefaultPlatformRegistry;
import com.tmp.core.registry.DefaultServiceRegistry;
import com.tmp.core.support.TestPlatformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultPlatformCoreRegistrationTest {

    private DefaultPlatformCore platformCore;

    @BeforeEach
    void setUp() {
        platformCore = new DefaultPlatformCore(
                new DefaultPlatformRegistry(),
                new DefaultServiceRegistry(),
                new DefaultCapabilityRegistry(),
                new SynchronousEventBus(),
                new PlatformConfiguration() {
                    @Override
                    public java.util.Optional<String> getString(String key) {
                        return java.util.Optional.empty();
                    }

                    @Override
                    public String getString(String key, String defaultValue) {
                        return defaultValue;
                    }

                    @Override
                    public boolean getBoolean(String key, boolean defaultValue) {
                        return defaultValue;
                    }
                },
                new DefaultLifecycleManager(),
                "TMP",
                "0.1.0-SNAPSHOT");
    }

    @Test
    void registerComponentRegistersInRegistryAndLifecycle() {
        TestPlatformComponent component = new TestPlatformComponent("atomic.test");

        platformCore.registerComponent(component);

        assertTrue(platformCore.platformRegistry().findById("atomic.test").isPresent());
        assertEquals(ComponentLifecycleState.REGISTERED, platformCore.lifecycleManager().stateOf("atomic.test"));
    }

    @Test
    void duplicateRegistrationDoesNotLeavePartialState() {
        TestPlatformComponent first = new TestPlatformComponent("dup.test");
        TestPlatformComponent second = new TestPlatformComponent("dup.test");

        platformCore.registerComponent(first);

        assertThrows(IllegalStateException.class, () -> platformCore.registerComponent(second));
        assertEquals(1, platformCore.platformRegistry().registeredComponents().size());
        assertEquals(ComponentLifecycleState.REGISTERED, platformCore.lifecycleManager().stateOf("dup.test"));
    }

    @Test
    void statusReportsRegisteredServiceCount() {
        PlatformComponentMetadata owner = new PlatformComponentMetadata(
                "svc-owner", "Owner", "1.0.0", ComponentType.SERVICE);
        platformCore.serviceRegistry().register(String.class, "ready", owner);
        platformCore.serviceRegistry().register(Integer.class, 42, owner);

        assertEquals(2, platformCore.status().registeredServices());
    }
}
