package com.tmp.capability.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityLifecycleState;
import com.tmp.capability.api.CapabilityVersion;
import com.tmp.capability.api.CommandDescriptor;
import com.tmp.capability.api.DependencyDescriptor;
import com.tmp.capability.api.PermissionDescriptor;
import com.tmp.capability.contribution.CapabilityContributionCatalogs;
import com.tmp.capability.contribution.CapabilityExternalContributionRegistry;
import com.tmp.capability.registry.CapabilityRegistration;
import com.tmp.capability.registry.CapabilityRegistry;
import com.tmp.core.api.EventBus;
import com.tmp.core.api.LifecycleManager;
import com.tmp.core.api.PlatformConfiguration;
import com.tmp.core.api.PlatformCore;
import com.tmp.core.api.PlatformRegistry;
import com.tmp.core.api.PlatformStatus;
import com.tmp.core.api.ServiceRegistry;
import com.tmp.core.api.component.PlatformComponent;
import com.tmp.core.event.SynchronousEventBus;
import com.tmp.core.registry.DefaultCapabilityRegistry;
import com.tmp.core.registry.DefaultServiceRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CapabilityLifecycleFailureAcceptanceTest {

    private CapabilityRegistry registry;
    private CapabilityContributionCatalogs catalogs;
    private CapabilityLifecycleManager lifecycle;
    private final List<String> stopOrder = new ArrayList<>();

    @BeforeEach
    void setUp() {
        registry = new CapabilityRegistry();
        catalogs = new CapabilityContributionCatalogs();
        DefaultCapabilityRegistry platformCapabilityRegistry = new DefaultCapabilityRegistry();
        DefaultServiceRegistry serviceRegistry = new DefaultServiceRegistry();
        lifecycle = new CapabilityLifecycleManager(
                registry,
                catalogs,
                new CapabilityExternalContributionRegistry(),
                new CapabilityEventSubscriptionRegistry(),
                new StubPlatformCore(platformCapabilityRegistry, serviceRegistry));
        stopOrder.clear();
    }

    @Test
    void onStopFailureDuringDeactivateLeavesCapabilityFailedNotStopped() {
        TrackingCapability capability = tracking("failing.stop");
        registerInitializedAndActive(capability);
        capability.failOnStop = true;

        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> lifecycle.deactivate(CapabilityId.of("failing.stop")));

        assertTrue(failure.getMessage().contains("stop failed"));
        assertEquals(CapabilityLifecycleState.FAILED, stateOf("failing.stop"));
        assertTrue(stopOrder.contains("failing.stop"));
    }

    @Test
    void onDeactivateFailureLeavesCapabilityFailedAfterSuccessfulStop() {
        TrackingCapability capability = tracking("failing.deactivate");
        registerInitializedAndActive(capability);
        capability.failOnDeactivate = true;

        IllegalStateException failure = assertThrows(
                IllegalStateException.class, () -> lifecycle.deactivate(CapabilityId.of("failing.deactivate")));

        assertTrue(failure.getMessage().contains("deactivate failed"));
        assertEquals(CapabilityLifecycleState.FAILED, stateOf("failing.deactivate"));
        assertTrue(stopOrder.contains("failing.deactivate"));
    }

    @Test
    void stopAllContinuesAfterOnStopFailureAndPreservesFirstExceptionWithSuppressed() {
        TrackingCapability first = tracking("stop.first");
        TrackingCapability failing = tracking("stop.failing");
        failing.failOnStop = true;
        TrackingCapability secondFailing = tracking("stop.failing.two");
        secondFailing.failOnStop = true;
        TrackingCapability third = tracking("stop.third");
        registerInitializedAndActive(first, failing, secondFailing, third);

        IllegalStateException failure = assertThrows(IllegalStateException.class, lifecycle::stopAll);

        assertTrue(failure.getMessage().contains("stop failed for stop.failing.two"));
        assertEquals(CapabilityLifecycleState.STOPPED, stateOf("stop.first"));
        assertEquals(CapabilityLifecycleState.FAILED, stateOf("stop.failing"));
        assertEquals(CapabilityLifecycleState.FAILED, stateOf("stop.failing.two"));
        assertEquals(CapabilityLifecycleState.STOPPED, stateOf("stop.third"));
        assertEquals(1, failure.getSuppressed().length);
        assertInstanceOf(IllegalStateException.class, failure.getSuppressed()[0]);
        assertTrue(failure.getSuppressed()[0].getMessage().contains("stop failed for stop.failing"));
        assertFalse(failure.getSuppressed()[0].getMessage().contains("stop.failing.two"));
    }

    @Test
    void independentCapabilityRemainsActiveWhenUnrelatedStopFailsDuringStopAll() {
        TrackingCapability root = tracking("chain.root");
        root.failOnStop = true;
        TrackingCapability dependent = tracking(
                "chain.leaf",
                DependencyDescriptor.of(CapabilityId.of("chain.root"), CapabilityVersion.of("1.0.0")));
        TrackingCapability independent = tracking("independent.capability");
        registerInitializedAndActive(root, dependent, independent);

        assertThrows(IllegalStateException.class, lifecycle::stopAll);

        assertEquals(CapabilityLifecycleState.FAILED, stateOf("chain.root"));
        assertEquals(CapabilityLifecycleState.STOPPED, stateOf("chain.leaf"));
        assertEquals(CapabilityLifecycleState.STOPPED, stateOf("independent.capability"));
    }

    private CapabilityLifecycleState stateOf(String id) {
        return registry.findById(CapabilityId.of(id)).orElseThrow().state();
    }

    private void registerInitializedAndActive(TrackingCapability... capabilities) {
        for (TrackingCapability capability : capabilities) {
            CapabilityId id = capability.descriptor().id();
            registry.reserve(id);
            registry.commit(new CapabilityRegistration(
                    capability.descriptor(), CapabilityLifecycleState.REGISTERED, capability));
            catalogs.registerInternalContributions(capability.descriptor());
        }
        lifecycle.initializeAll();
        lifecycle.activateAll();
    }

    private TrackingCapability tracking(String id, DependencyDescriptor... dependencies) {
        CapabilityDescriptor descriptor = CapabilityDescriptor.builder()
                .id(CapabilityId.of(id))
                .name("Capability " + id)
                .version(CapabilityVersion.of("1.0.0"))
                .description("Lifecycle failure acceptance test")
                .dependencies(List.of(dependencies))
                .permissions(List.of(PermissionDescriptor.of(id + ".perm", "Perm", "desc")))
                .commands(List.of(CommandDescriptor.of(id + ".cmd", "Cmd", List.of())))
                .build();
        return new TrackingCapability(descriptor);
    }

    private final class TrackingCapability implements Capability {
        private final CapabilityDescriptor descriptor;
        private boolean failOnStop;
        private boolean failOnDeactivate;

        private TrackingCapability(CapabilityDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public CapabilityDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public void onInitialize() {
            // test double
        }

        @Override
        public void onActivate() {
            // test double
        }

        @Override
        public void onDeactivate() {
            if (failOnDeactivate) {
                throw new IllegalStateException("deactivate failed for " + descriptor.id());
            }
        }

        @Override
        public void onStop() {
            stopOrder.add(descriptor.id().value());
            if (failOnStop) {
                throw new IllegalStateException("stop failed for " + descriptor.id());
            }
        }
    }

    private static final class StubPlatformCore implements PlatformCore {
        private final com.tmp.core.api.CapabilityRegistry capabilityRegistry;
        private final ServiceRegistry serviceRegistry;
        private final EventBus eventBus = new SynchronousEventBus();

        private StubPlatformCore(
                com.tmp.core.api.CapabilityRegistry capabilityRegistry, ServiceRegistry serviceRegistry) {
            this.capabilityRegistry = capabilityRegistry;
            this.serviceRegistry = serviceRegistry;
        }

        @Override
        public void registerComponent(PlatformComponent component) {
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public PlatformRegistry platformRegistry() {
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public ServiceRegistry serviceRegistry() {
            return serviceRegistry;
        }

        @Override
        public com.tmp.core.api.CapabilityRegistry capabilityRegistry() {
            return capabilityRegistry;
        }

        @Override
        public EventBus eventBus() {
            return eventBus;
        }

        @Override
        public PlatformConfiguration configuration() {
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public LifecycleManager lifecycleManager() {
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public PlatformStatus status() {
            throw new UnsupportedOperationException("not used in this test");
        }
    }
}
