package com.tmp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.tmp.core.api.PlatformConfiguration;
import com.tmp.core.api.event.platform.PlatformStoppingEvent;
import com.tmp.core.event.SynchronousEventBus;
import com.tmp.core.lifecycle.DefaultLifecycleManager;
import com.tmp.core.registry.DefaultCapabilityRegistry;
import com.tmp.core.registry.DefaultPlatformRegistry;
import com.tmp.core.registry.DefaultServiceRegistry;
import com.tmp.core.support.TestPlatformComponent;
import com.tmp.core.api.component.ComponentLifecycleState;
import org.junit.jupiter.api.Test;
import org.springframework.context.event.ContextClosedEvent;

class PlatformCoreLifecycleListenerTest {

    @Test
    void stopAllRunsEvenWhenStoppingEventHandlerFails() {
        SynchronousEventBus eventBus = new SynchronousEventBus();
        DefaultLifecycleManager lifecycleManager = new DefaultLifecycleManager();
        DefaultPlatformCore platformCore = new DefaultPlatformCore(
                new DefaultPlatformRegistry(),
                new DefaultServiceRegistry(),
                new DefaultCapabilityRegistry(),
                eventBus,
                stubConfiguration(),
                lifecycleManager,
                "TMP",
                "0.1.0-SNAPSHOT");
        TestPlatformComponent component = new TestPlatformComponent("shutdown.test");
        platformCore.registerComponent(component);
        platformCore.lifecycleManager().startAll();

        eventBus.subscribePlatform(PlatformStoppingEvent.class, event -> {
            throw new IllegalStateException("stopping handler failed");
        });

        PlatformCoreAutoConfiguration.PlatformCoreLifecycleListener listener =
                new PlatformCoreAutoConfiguration.PlatformCoreLifecycleListener(platformCore, eventBus);

        assertThrows(IllegalStateException.class, () -> listener.onContextClosed(mock(ContextClosedEvent.class)));

        assertEquals(ComponentLifecycleState.STOPPED, platformCore.lifecycleManager().platformState());
        assertEquals(ComponentLifecycleState.STOPPED, platformCore.lifecycleManager().stateOf("shutdown.test"));
        assertEquals(1, component.stopCount());
    }

    private static PlatformConfiguration stubConfiguration() {
        return new PlatformConfiguration() {
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
        };
    }
}
