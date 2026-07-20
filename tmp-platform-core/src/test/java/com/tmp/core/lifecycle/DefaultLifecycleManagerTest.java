package com.tmp.core.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.core.api.CapabilityRegistry;
import com.tmp.core.api.EventBus;
import com.tmp.core.api.LifecycleManager;
import com.tmp.core.api.PlatformConfiguration;
import com.tmp.core.api.PlatformCore;
import com.tmp.core.api.PlatformRegistry;
import com.tmp.core.api.PlatformStatus;
import com.tmp.core.api.ServiceRegistry;
import com.tmp.core.api.component.ComponentLifecycleState;
import com.tmp.core.api.component.PlatformComponent;
import com.tmp.core.api.component.PlatformComponentMetadata;
import com.tmp.core.registry.DefaultCapabilityRegistry;
import com.tmp.core.registry.DefaultPlatformRegistry;
import com.tmp.core.registry.DefaultServiceRegistry;
import com.tmp.core.support.TestPlatformComponent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultLifecycleManagerTest {

    private DefaultLifecycleManager lifecycleManager;
    private PlatformCore platformCore;

    @BeforeEach
    void setUp() {
        TrackingComponent.trace.clear();
        lifecycleManager = new DefaultLifecycleManager();
        platformCore = new PlatformCoreStub(lifecycleManager);
        lifecycleManager.attachPlatformCore(platformCore);
    }

    @Test
    void startsAndStopsRegisteredComponentsInReverseOrder() {
        TrackingComponent first = new TrackingComponent("first");
        TrackingComponent second = new TrackingComponent("second");
        lifecycleManager.registerInternal(first);
        lifecycleManager.registerInternal(second);

        lifecycleManager.startAll();
        assertEquals(ComponentLifecycleState.STARTED, lifecycleManager.platformState());
        assertEquals(List.of("first:start", "second:start"), TrackingComponent.trace);

        TrackingComponent.trace.clear();
        lifecycleManager.stopAll();
        assertEquals(ComponentLifecycleState.STOPPED, lifecycleManager.platformState());
        assertEquals(List.of("second:stop", "first:stop"), TrackingComponent.trace);
    }

    @Test
    void initializeFailureMarksComponentAndPlatformFailedAndRollsBack() {
        TrackingComponent first = new TrackingComponent("first");
        TrackingComponent failing = new TrackingComponent("failing") {
            @Override
            public void initialize(PlatformCore platformCore) {
                throw new IllegalStateException("initialize failed");
            }
        };
        lifecycleManager.registerInternal(first);
        lifecycleManager.registerInternal(failing);

        IllegalStateException failure = assertThrows(IllegalStateException.class, lifecycleManager::startAll);

        assertEquals("initialize failed", failure.getMessage());
        assertEquals(ComponentLifecycleState.FAILED, lifecycleManager.platformState());
        assertEquals(ComponentLifecycleState.STOPPED, lifecycleManager.stateOf("first"));
        assertEquals(ComponentLifecycleState.FAILED, lifecycleManager.stateOf("failing"));
    }

    @Test
    void startFailureRollsBackPreviouslyStartedComponentsInReverseOrder() {
        TrackingComponent first = new TrackingComponent("first");
        TrackingComponent second = new TrackingComponent("second") {
            @Override
            public void start() {
                throw new IllegalStateException("start failed");
            }
        };
        lifecycleManager.registerInternal(first);
        lifecycleManager.registerInternal(second);

        IllegalStateException failure = assertThrows(IllegalStateException.class, lifecycleManager::startAll);

        assertEquals("start failed", failure.getMessage());
        assertEquals(ComponentLifecycleState.FAILED, lifecycleManager.platformState());
        assertEquals(ComponentLifecycleState.STOPPED, lifecycleManager.stateOf("first"));
        assertEquals(ComponentLifecycleState.FAILED, lifecycleManager.stateOf("second"));
        assertTrue(TrackingComponent.trace.contains("first:stop"));
    }

    @Test
    void rollbackErrorsAreSuppressedWithoutHidingOriginalFailure() {
        TrackingComponent first = new TrackingComponent("first") {
            @Override
            public void stop() {
                throw new IllegalStateException("rollback stop failed");
            }
        };
        TrackingComponent failing = new TrackingComponent("failing") {
            @Override
            public void start() {
                throw new IllegalStateException("start failed");
            }
        };
        lifecycleManager.registerInternal(first);
        lifecycleManager.registerInternal(failing);

        IllegalStateException failure = assertThrows(IllegalStateException.class, lifecycleManager::startAll);

        assertEquals("start failed", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("rollback stop failed", failure.getSuppressed()[0].getMessage());
    }

    @Test
    void stopFailureMarksPlatformFailed() {
        TrackingComponent component = new TrackingComponent("stop-fail") {
            @Override
            public void stop() {
                throw new IllegalStateException("stop failed");
            }
        };
        lifecycleManager.registerInternal(component);
        lifecycleManager.startAll();

        IllegalStateException failure = assertThrows(IllegalStateException.class, lifecycleManager::stopAll);

        assertEquals("stop failed", failure.getMessage());
        assertEquals(ComponentLifecycleState.FAILED, lifecycleManager.platformState());
        assertEquals(ComponentLifecycleState.FAILED, lifecycleManager.stateOf("stop-fail"));
    }

    @Test
    void repeatedStartAllIsRejected() {
        lifecycleManager.registerInternal(new TrackingComponent("one"));
        lifecycleManager.startAll();

        assertThrows(IllegalStateException.class, lifecycleManager::startAll);
    }

    @Test
    void repeatedStopAllIsRejected() {
        lifecycleManager.registerInternal(new TrackingComponent("one"));
        lifecycleManager.startAll();
        lifecycleManager.stopAll();

        assertThrows(IllegalStateException.class, lifecycleManager::stopAll);
    }

    static class TrackingComponent extends TestPlatformComponent {

        static final List<String> trace = new ArrayList<>();

        TrackingComponent(String id) {
            super(id);
        }

        @Override
        public void start() {
            trace.add(metadata().id() + ":start");
        }

        @Override
        public void stop() {
            trace.add(metadata().id() + ":stop");
        }
    }

    private static final class PlatformCoreStub implements PlatformCore {

        private final LifecycleManager lifecycleManager;

        private PlatformCoreStub(LifecycleManager lifecycleManager) {
            this.lifecycleManager = lifecycleManager;
        }

        @Override
        public void registerComponent(PlatformComponent component) {
            throw new UnsupportedOperationException("not required");
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
            throw new UnsupportedOperationException("not required");
        }

        @Override
        public PlatformConfiguration configuration() {
            throw new UnsupportedOperationException("not required");
        }

        @Override
        public LifecycleManager lifecycleManager() {
            return lifecycleManager;
        }

        @Override
        public PlatformStatus status() {
            throw new UnsupportedOperationException("not required");
        }
    }
}
