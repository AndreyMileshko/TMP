package com.tmp.capability.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class CapabilityLifecycleManagerTest {

    private CapabilityRegistry registry;
    private CapabilityContributionCatalogs catalogs;
    private CapabilityLifecycleManager lifecycle;
    private final List<String> initializeOrder = new ArrayList<>();
    private final List<String> activateOrder = new ArrayList<>();
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
        initializeOrder.clear();
        activateOrder.clear();
        stopOrder.clear();
    }

    @Test
    void successfulRegistrationInitializationActivationChain() {
        TrackingCapability root = tracking("root.capability");
        TrackingCapability leaf = tracking(
                "leaf.capability",
                DependencyDescriptor.of(CapabilityId.of("root.capability"), CapabilityVersion.of("1.0.0")));
        registerAsRegistered(root);
        registerAsRegistered(leaf);
        catalogs.registerInternalContributions(root.descriptor());
        catalogs.registerInternalContributions(leaf.descriptor());

        lifecycle.initializeAll();
        assertEquals(CapabilityLifecycleState.INITIALIZED, stateOf("root.capability"));
        assertEquals(CapabilityLifecycleState.INITIALIZED, stateOf("leaf.capability"));
        assertEquals(List.of("root.capability", "leaf.capability"), initializeOrder);

        lifecycle.activateAll();
        assertEquals(CapabilityLifecycleState.ACTIVE, stateOf("root.capability"));
        assertEquals(CapabilityLifecycleState.ACTIVE, stateOf("leaf.capability"));
        assertEquals(List.of("root.capability", "leaf.capability"), activateOrder);
    }

    @Test
    void invalidTransitionAttemptsRejected() {
        TrackingCapability capability = tracking("solo.capability");
        registerAsRegistered(capability);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class, () -> lifecycle.deactivate(CapabilityId.of("solo.capability")));
        assertTrue(failure.getMessage().contains("REGISTERED"));
        assertEquals(CapabilityLifecycleState.REGISTERED, stateOf("solo.capability"));
    }

    @Test
    void repeatedActivationRejected() {
        TrackingCapability capability = tracking("solo.capability");
        registerAsRegistered(capability);
        lifecycle.initializeAll();
        lifecycle.activateAll();

        IllegalStateException failure = assertThrows(IllegalStateException.class, lifecycle::activateAll);
        assertTrue(failure.getMessage().contains("already ACTIVE"));
        assertEquals(CapabilityLifecycleState.ACTIVE, stateOf("solo.capability"));
    }

    @Test
    void stopAllStopsInExactReverseOrderOfInitialization() {
        TrackingCapability a = tracking("a.capability");
        TrackingCapability b = tracking(
                "b.capability",
                DependencyDescriptor.of(CapabilityId.of("a.capability"), CapabilityVersion.of("1.0.0")));
        TrackingCapability c = tracking(
                "c.capability",
                DependencyDescriptor.of(CapabilityId.of("b.capability"), CapabilityVersion.of("1.0.0")));
        registerAsRegistered(a);
        registerAsRegistered(b);
        registerAsRegistered(c);

        lifecycle.initializeAll();
        lifecycle.activateAll();
        assertEquals(List.of("a.capability", "b.capability", "c.capability"), initializeOrder);

        lifecycle.stopAll();

        assertEquals(List.of("c.capability", "b.capability", "a.capability"), stopOrder);
        assertEquals(CapabilityLifecycleState.STOPPED, stateOf("a.capability"));
        assertEquals(CapabilityLifecycleState.STOPPED, stateOf("b.capability"));
        assertEquals(CapabilityLifecycleState.STOPPED, stateOf("c.capability"));
    }

    @Test
    void normalDeactivationRemovesCatalogEntriesAndDoesNotCascade() {
        TrackingCapability root = tracking("root.capability");
        TrackingCapability independent = tracking("independent.capability");
        registerAsRegistered(root);
        registerAsRegistered(independent);
        catalogs.registerInternalContributions(root.descriptor());
        catalogs.registerInternalContributions(independent.descriptor());

        lifecycle.initializeAll();
        lifecycle.activateAll();

        lifecycle.deactivate(CapabilityId.of("root.capability"));

        assertEquals(CapabilityLifecycleState.DEACTIVATED, stateOf("root.capability"));
        assertEquals(CapabilityLifecycleState.ACTIVE, stateOf("independent.capability"));
        assertTrue(catalogs.permissions().ownerOf("root.capability.perm").isEmpty());
        assertEquals(
                CapabilityId.of("independent.capability"),
                catalogs.permissions().ownerOf("independent.capability.perm").orElseThrow());
    }

    @Test
    void deactivationWithActiveDependentsRejected() {
        TrackingCapability root = tracking("root.capability");
        TrackingCapability leaf = tracking(
                "leaf.capability",
                DependencyDescriptor.of(CapabilityId.of("root.capability"), CapabilityVersion.of("1.0.0")));
        registerAsRegistered(root);
        registerAsRegistered(leaf);
        lifecycle.initializeAll();
        lifecycle.activateAll();

        IllegalStateException failure = assertThrows(
                IllegalStateException.class, () -> lifecycle.deactivate(CapabilityId.of("root.capability")));
        assertTrue(failure.getMessage().contains("leaf.capability"));
        assertEquals(CapabilityLifecycleState.ACTIVE, stateOf("root.capability"));
        assertEquals(CapabilityLifecycleState.ACTIVE, stateOf("leaf.capability"));
    }

    @Test
    void failedInitializationIsolatesFailedCapabilityAndDependents() {
        TrackingCapability root = tracking("root.capability");
        root.failOnInitialize = true;
        TrackingCapability leaf = tracking(
                "leaf.capability",
                DependencyDescriptor.of(CapabilityId.of("root.capability"), CapabilityVersion.of("1.0.0")));
        TrackingCapability independent = tracking("independent.capability");
        registerAsRegistered(root);
        registerAsRegistered(leaf);
        registerAsRegistered(independent);

        lifecycle.initializeAll();

        assertEquals(CapabilityLifecycleState.FAILED, stateOf("root.capability"));
        assertEquals(CapabilityLifecycleState.FAILED, stateOf("leaf.capability"));
        assertEquals(CapabilityLifecycleState.INITIALIZED, stateOf("independent.capability"));

        lifecycle.activateAll();

        assertEquals(CapabilityLifecycleState.FAILED, stateOf("root.capability"));
        assertEquals(CapabilityLifecycleState.FAILED, stateOf("leaf.capability"));
        assertEquals(CapabilityLifecycleState.ACTIVE, stateOf("independent.capability"));
    }

    @Test
    void failedActivationIsolatesFailedCapabilityAndDependents() {
        TrackingCapability root = tracking("root.capability");
        root.failOnActivate = true;
        TrackingCapability leaf = tracking(
                "leaf.capability",
                DependencyDescriptor.of(CapabilityId.of("root.capability"), CapabilityVersion.of("1.0.0")));
        TrackingCapability independent = tracking("independent.capability");
        registerAsRegistered(root);
        registerAsRegistered(leaf);
        registerAsRegistered(independent);

        lifecycle.initializeAll();
        lifecycle.activateAll();

        assertEquals(CapabilityLifecycleState.FAILED, stateOf("root.capability"));
        assertEquals(CapabilityLifecycleState.FAILED, stateOf("leaf.capability"));
        assertEquals(CapabilityLifecycleState.ACTIVE, stateOf("independent.capability"));
    }

    @Test
    void capabilityWhoseDependencyFailedNeverReachesActive() {
        TrackingCapability root = tracking("root.capability");
        root.failOnInitialize = true;
        TrackingCapability leaf = tracking(
                "leaf.capability",
                DependencyDescriptor.of(CapabilityId.of("root.capability"), CapabilityVersion.of("1.0.0")));
        registerAsRegistered(root);
        registerAsRegistered(leaf);

        lifecycle.initializeAll();
        lifecycle.activateAll();

        assertEquals(CapabilityLifecycleState.FAILED, stateOf("leaf.capability"));
        assertTrue(activateOrder.isEmpty() || !activateOrder.contains("leaf.capability"));
    }

    private CapabilityLifecycleState stateOf(String id) {
        return registry.findById(CapabilityId.of(id)).orElseThrow().state();
    }

    private void registerAsRegistered(TrackingCapability capability) {
        CapabilityId id = capability.descriptor().id();
        registry.reserve(id);
        registry.commit(new CapabilityRegistration(
                capability.descriptor(), CapabilityLifecycleState.REGISTERED, capability));
    }

    private TrackingCapability tracking(String id, DependencyDescriptor... dependencies) {
        CapabilityDescriptor descriptor = CapabilityDescriptor.builder()
                .id(CapabilityId.of(id))
                .name("Capability " + id)
                .version(CapabilityVersion.of("1.0.0"))
                .description("Lifecycle test capability")
                .dependencies(List.of(dependencies))
                .permissions(List.of(PermissionDescriptor.of(id + ".perm", "Perm", "desc")))
                .commands(List.of(CommandDescriptor.of(id + ".cmd", "Cmd", List.of())))
                .build();
        return new TrackingCapability(descriptor);
    }

    private final class TrackingCapability implements Capability {
        private final CapabilityDescriptor descriptor;
        private boolean failOnInitialize;
        private boolean failOnActivate;

        private TrackingCapability(CapabilityDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public CapabilityDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public void onInitialize() {
            initializeOrder.add(descriptor.id().value());
            if (failOnInitialize) {
                throw new IllegalStateException("initialize failed for " + descriptor.id());
            }
        }

        @Override
        public void onActivate() {
            activateOrder.add(descriptor.id().value());
            if (failOnActivate) {
                throw new IllegalStateException("activate failed for " + descriptor.id());
            }
        }

        @Override
        public void onDeactivate() {
            // test double: no-op beyond tracking via stop/deactivate path
        }

        @Override
        public void onStop() {
            stopOrder.add(descriptor.id().value());
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
