package com.tmp.core.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tmp.core.api.CapabilityRegistry;
import com.tmp.core.api.EventBus;
import com.tmp.core.api.LifecycleManager;
import com.tmp.core.api.PlatformConfiguration;
import com.tmp.core.api.PlatformCore;
import com.tmp.core.api.PlatformRegistry;
import com.tmp.core.api.PlatformStatus;
import com.tmp.core.api.ServiceRegistry;
import com.tmp.core.api.component.ComponentLifecycleState;
import com.tmp.core.registry.DefaultCapabilityRegistry;
import com.tmp.core.registry.DefaultPlatformRegistry;
import com.tmp.core.registry.DefaultServiceRegistry;
import com.tmp.core.support.TestPlatformComponent;
import org.junit.jupiter.api.Test;

class DefaultLifecycleManagerTest {

    @Test
    void startsAndStopsRegisteredComponents() {
        DefaultLifecycleManager lifecycleManager = new DefaultLifecycleManager();
        DefaultPlatformCoreStub platformCore = new DefaultPlatformCoreStub(lifecycleManager);
        lifecycleManager.attachPlatformCore(platformCore);

        TestPlatformComponent component = new TestPlatformComponent("life.test");
        lifecycleManager.registerComponent(component);

        lifecycleManager.startAll();
        assertEquals(ComponentLifecycleState.STARTED, lifecycleManager.stateOf("life.test"));
        assertEquals(1, component.startCount());

        lifecycleManager.stopAll();
        assertEquals(ComponentLifecycleState.STOPPED, lifecycleManager.stateOf("life.test"));
        assertEquals(1, component.stopCount());
    }

    private static final class DefaultPlatformCoreStub implements PlatformCore {

        private final LifecycleManager lifecycleManager;

        private DefaultPlatformCoreStub(DefaultLifecycleManager lifecycleManager) {
            this.lifecycleManager = lifecycleManager;
        }

        @Override
        public PlatformRegistry platformRegistry() {
            return new DefaultPlatformRegistry();
        }

        @Override
        public ServiceRegistry serviceRegistry() {
            return new DefaultServiceRegistry();
        }

        @Override
        public CapabilityRegistry capabilityRegistry() {
            return new DefaultCapabilityRegistry();
        }

        @Override
        public EventBus eventBus() {
            throw new UnsupportedOperationException("not required for lifecycle test");
        }

        @Override
        public PlatformConfiguration configuration() {
            throw new UnsupportedOperationException("not required for lifecycle test");
        }

        @Override
        public LifecycleManager lifecycleManager() {
            return lifecycleManager;
        }

        @Override
        public PlatformStatus status() {
            throw new UnsupportedOperationException("not required for lifecycle test");
        }
    }
}
