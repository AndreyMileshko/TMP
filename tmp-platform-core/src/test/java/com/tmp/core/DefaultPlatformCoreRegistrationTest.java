package com.tmp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.core.api.PlatformConfiguration;
import com.tmp.core.api.PlatformCore;
import com.tmp.core.api.component.ComponentLifecycleState;
import com.tmp.core.api.component.ComponentType;
import com.tmp.core.api.component.PlatformComponent;
import com.tmp.core.api.component.PlatformComponentMetadata;
import com.tmp.core.event.SynchronousEventBus;
import com.tmp.core.lifecycle.DefaultLifecycleManager;
import com.tmp.core.registry.DefaultCapabilityRegistry;
import com.tmp.core.registry.DefaultPlatformRegistry;
import com.tmp.core.registry.DefaultServiceRegistry;
import com.tmp.core.support.TestPlatformComponent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultPlatformCoreRegistrationTest {

    private DefaultPlatformCore platformCore;

    @BeforeEach
    void setUp() {
        platformCore = createPlatformCore(new DefaultLifecycleManager());
    }

    @Test
    void registerComponentRegistersInRegistryAndLifecycle() {
        TestPlatformComponent component = new TestPlatformComponent("atomic.test");

        platformCore.registerComponent(component);

        assertTrue(platformCore.platformRegistry().findById("atomic.test").isPresent());
        assertEquals(ComponentLifecycleState.REGISTERED, platformCore.lifecycleManager().stateOf("atomic.test"));
    }

    @Test
    void registrationBeforeStartSucceeds() {
        TestPlatformComponent first = new TestPlatformComponent("before.start.1");
        TestPlatformComponent second = new TestPlatformComponent("before.start.2");

        platformCore.registerComponent(first);
        platformCore.registerComponent(second);

        assertEquals(2, platformCore.platformRegistry().registeredComponents().size());
        assertEquals(ComponentLifecycleState.REGISTERED, platformCore.lifecycleManager().platformState());
    }

    @Test
    void registrationAfterStopSucceeds() {
        TestPlatformComponent existing = new TestPlatformComponent("existing.after.stop");
        platformCore.registerComponent(existing);
        platformCore.lifecycleManager().startAll();
        platformCore.lifecycleManager().stopAll();

        TestPlatformComponent added = new TestPlatformComponent("added.after.stop");
        platformCore.registerComponent(added);

        assertTrue(platformCore.platformRegistry().findById("added.after.stop").isPresent());
        assertEquals(ComponentLifecycleState.REGISTERED, platformCore.lifecycleManager().stateOf("added.after.stop"));
        assertEquals(ComponentLifecycleState.STOPPED, platformCore.lifecycleManager().platformState());
    }

    @Test
    void registrationWhileStartedFailsWithoutPartialState() {
        TestPlatformComponent existing = new TestPlatformComponent("existing.started");
        platformCore.registerComponent(existing);
        platformCore.lifecycleManager().startAll();

        TestPlatformComponent rejected = new TestPlatformComponent("rejected.started");
        IllegalStateException failure = assertThrows(
                IllegalStateException.class, () -> platformCore.registerComponent(rejected));

        assertTrue(failure.getMessage().contains("STARTED"));
        assertFalse(platformCore.platformRegistry().findById("rejected.started").isPresent());
        assertEquals(1, platformCore.platformRegistry().registeredComponents().size());
        assertEquals(ComponentLifecycleState.STARTED, platformCore.lifecycleManager().platformState());
    }

    @Test
    void registrationWhileFailedFailsWithoutPartialState() {
        FailingStartComponent failing = new FailingStartComponent("failed.platform");
        platformCore.registerComponent(failing);
        assertThrows(RuntimeException.class, () -> platformCore.lifecycleManager().startAll());

        TestPlatformComponent rejected = new TestPlatformComponent("rejected.failed");
        IllegalStateException failure = assertThrows(
                IllegalStateException.class, () -> platformCore.registerComponent(rejected));

        assertTrue(failure.getMessage().contains("FAILED"));
        assertFalse(platformCore.platformRegistry().findById("rejected.failed").isPresent());
        assertEquals(1, platformCore.platformRegistry().registeredComponents().size());
        assertEquals(ComponentLifecycleState.FAILED, platformCore.lifecycleManager().platformState());
    }

    @Test
    void registrationWhileInitializingFailsWithoutPartialState() throws Exception {
        BlockingInitializeComponent blocking = new BlockingInitializeComponent("blocking.init");
        platformCore.registerComponent(blocking);

        AtomicReference<IllegalStateException> registrationFailure = new AtomicReference<>();
        Thread startupThread = new Thread(() -> platformCore.lifecycleManager().startAll());
        startupThread.start();

        blocking.waitUntilInitializeEntered();
        assertEquals(ComponentLifecycleState.INITIALIZING, platformCore.lifecycleManager().platformState());

        TestPlatformComponent rejected = new TestPlatformComponent("rejected.initializing");
        registrationFailure.set(assertThrows(
                IllegalStateException.class, () -> platformCore.registerComponent(rejected)));

        blocking.releaseInitialize();
        startupThread.join(TimeUnit.SECONDS.toMillis(5));

        assertTrue(registrationFailure.get().getMessage().contains("INITIALIZING"));
        assertFalse(platformCore.platformRegistry().findById("rejected.initializing").isPresent());
        assertEquals(1, platformCore.platformRegistry().registeredComponents().size());
        assertEquals(ComponentLifecycleState.STARTED, platformCore.lifecycleManager().platformState());
    }

    @Test
    void registrationWhileStoppingFailsWithoutPartialState() throws Exception {
        TestPlatformComponent existing = new TestPlatformComponent("existing.stopping");
        BlockingStopComponent blocking = new BlockingStopComponent("blocking.stop");
        platformCore.registerComponent(existing);
        platformCore.registerComponent(blocking);
        platformCore.lifecycleManager().startAll();

        AtomicReference<IllegalStateException> registrationFailure = new AtomicReference<>();
        Thread shutdownThread = new Thread(() -> platformCore.lifecycleManager().stopAll());
        shutdownThread.start();

        blocking.waitUntilStopEntered();
        assertEquals(ComponentLifecycleState.STOPPING, platformCore.lifecycleManager().platformState());

        TestPlatformComponent rejected = new TestPlatformComponent("rejected.stopping");
        registrationFailure.set(assertThrows(
                IllegalStateException.class, () -> platformCore.registerComponent(rejected)));

        blocking.releaseStop();
        shutdownThread.join(TimeUnit.SECONDS.toMillis(5));

        assertTrue(registrationFailure.get().getMessage().contains("STOPPING"));
        assertFalse(platformCore.platformRegistry().findById("rejected.stopping").isPresent());
        assertEquals(2, platformCore.platformRegistry().registeredComponents().size());
        assertEquals(ComponentLifecycleState.STOPPED, platformCore.lifecycleManager().platformState());
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

    private static DefaultPlatformCore createPlatformCore(DefaultLifecycleManager lifecycleManager) {
        return new DefaultPlatformCore(
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
                lifecycleManager,
                "TMP",
                "0.1.0-SNAPSHOT");
    }

    private static final class FailingStartComponent extends TestPlatformComponent {

        FailingStartComponent(String id) {
            super(id);
        }

        @Override
        public void start() {
            throw new RuntimeException("start failed");
        }
    }

    private static final class BlockingInitializeComponent extends TestPlatformComponent {

        private final CountDownLatch initializeEntered = new CountDownLatch(1);
        private final CountDownLatch initializeContinue = new CountDownLatch(1);

        BlockingInitializeComponent(String id) {
            super(id);
        }

        @Override
        public void initialize(PlatformCore platformCore) {
            initializeEntered.countDown();
            try {
                if (!initializeContinue.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("initialize continue latch timed out");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("initialize interrupted", interrupted);
            }
        }

        void waitUntilInitializeEntered() throws InterruptedException {
            if (!initializeEntered.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("initialize entry latch timed out");
            }
        }

        void releaseInitialize() {
            initializeContinue.countDown();
        }
    }

    private static final class BlockingStopComponent extends TestPlatformComponent {

        private final CountDownLatch stopEntered = new CountDownLatch(1);
        private final CountDownLatch stopContinue = new CountDownLatch(1);

        BlockingStopComponent(String id) {
            super(id);
        }

        @Override
        public void stop() {
            stopEntered.countDown();
            try {
                if (!stopContinue.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("stop continue latch timed out");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("stop interrupted", interrupted);
            }
        }

        void waitUntilStopEntered() throws InterruptedException {
            if (!stopEntered.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("stop entry latch timed out");
            }
        }

        void releaseStop() {
            stopContinue.countDown();
        }
    }
}

