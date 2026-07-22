package com.tmp.capability.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityLifecycleState;
import com.tmp.capability.api.CapabilityVersion;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CapabilityRegistryTest {

    private static CapabilityDescriptor descriptorFor(String id) {
        return CapabilityDescriptor.builder()
                .id(CapabilityId.of(id))
                .name("Capability " + id)
                .version(CapabilityVersion.of("1.0.0"))
                .description("Test capability " + id)
                .build();
    }

    private static Capability capabilityFor(CapabilityDescriptor descriptor) {
        return new Capability() {
            @Override
            public CapabilityDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public void onInitialize() {
                // test double: no-op
            }

            @Override
            public void onActivate() {
                // test double: no-op
            }

            @Override
            public void onDeactivate() {
                // test double: no-op
            }

            @Override
            public void onStop() {
                // test double: no-op
            }
        };
    }

    private static CapabilityRegistration registrationFor(String id, CapabilityLifecycleState state) {
        CapabilityDescriptor descriptor = descriptorFor(id);
        return new CapabilityRegistration(descriptor, state, capabilityFor(descriptor));
    }

    @Test
    void reserveCommitFindByIdAndFindAllWork() {
        CapabilityRegistry registry = new CapabilityRegistry();
        CapabilityId id = CapabilityId.of("sample.capability");
        registry.reserve(id);

        assertTrue(registry.findById(id).isEmpty(), "reservation alone must not be visible in findById");
        assertTrue(registry.findAll().isEmpty(), "reservation alone must not be visible in findAll");

        registry.commit(registrationFor("sample.capability", CapabilityLifecycleState.REGISTERED));

        assertTrue(registry.findById(id).isPresent());
        assertEquals(CapabilityLifecycleState.REGISTERED, registry.findById(id).orElseThrow().state());
        assertEquals(1, registry.findAll().size());
    }

    @Test
    void duplicateIdRejectedAtReserve() {
        CapabilityRegistry registry = new CapabilityRegistry();
        CapabilityId id = CapabilityId.of("dup.capability");
        registry.reserve(id);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> registry.reserve(id));
        assertTrue(failure.getMessage().contains("dup.capability"));
    }

    @Test
    void duplicateIdRejectedAtCommit() {
        CapabilityRegistry registry = new CapabilityRegistry();
        registry.reserve(CapabilityId.of("dup.commit"));
        registry.commit(registrationFor("dup.commit", CapabilityLifecycleState.REGISTERED));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> registry.commit(registrationFor("dup.commit", CapabilityLifecycleState.REGISTERED)));
        assertTrue(failure.getMessage().contains("dup.commit"));
    }

    @Test
    void releaseAfterReserveAllowsSubsequentReserveToSucceed() {
        CapabilityRegistry registry = new CapabilityRegistry();
        CapabilityId id = CapabilityId.of("retry.capability");
        registry.reserve(id);
        registry.release(id);

        registry.reserve(id);

        assertTrue(registry.findById(id).isEmpty());
    }

    @Test
    void stateUpdateIsReflectedInSnapshot() {
        CapabilityRegistry registry = new CapabilityRegistry();
        CapabilityId id = CapabilityId.of("state.capability");
        registry.reserve(id);
        registry.commit(registrationFor("state.capability", CapabilityLifecycleState.REGISTERED));

        registry.updateState(id, CapabilityLifecycleState.ACTIVE);

        assertEquals(CapabilityLifecycleState.ACTIVE, registry.findById(id).orElseThrow().state());
        assertEquals(CapabilityLifecycleState.ACTIVE, registry.findAll().get(0).state());
    }

    @Test
    void updateStateOnUnregisteredIdThrows() {
        CapabilityRegistry registry = new CapabilityRegistry();

        assertThrows(
                IllegalStateException.class,
                () -> registry.updateState(CapabilityId.of("missing.capability"), CapabilityLifecycleState.ACTIVE));
    }

    @Test
    void findAllReturnsImmutableSnapshot() {
        CapabilityRegistry registry = new CapabilityRegistry();
        registry.reserve(CapabilityId.of("immutable.capability"));
        registry.commit(registrationFor("immutable.capability", CapabilityLifecycleState.REGISTERED));

        List<CapabilityRegistration> snapshot = registry.findAll();

        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.add(registrationFor("another.capability", CapabilityLifecycleState.REGISTERED)));
    }

    @Test
    void findAllIsSortedDeterministicallyById() {
        CapabilityRegistry registry = new CapabilityRegistry();
        for (String id : List.of("charlie.capability", "alpha.capability", "bravo.capability")) {
            registry.reserve(CapabilityId.of(id));
            registry.commit(registrationFor(id, CapabilityLifecycleState.REGISTERED));
        }

        List<String> ids = registry.findAll().stream()
                .map(registration -> registration.descriptor().id().value())
                .toList();

        assertEquals(List.of("alpha.capability", "bravo.capability", "charlie.capability"), ids);
    }

    @Test
    void concurrentFindAllDuringConcurrentReserveAndCommitProducesNoConcurrentModificationException()
            throws Exception {
        CapabilityRegistry registry = new CapabilityRegistry();
        int capabilityCount = 200;
        CyclicBarrier gate = new CyclicBarrier(2);

        Thread registrationThread = new Thread(() -> {
            awaitGate(gate);
            for (int i = 0; i < capabilityCount; i++) {
                String id = "concurrent.capability." + i;
                registry.reserve(CapabilityId.of(id));
                registry.commit(registrationFor(id, CapabilityLifecycleState.REGISTERED));
            }
        });

        Thread readerThread = new Thread(() -> {
            awaitGate(gate);
            for (int i = 0; i < capabilityCount; i++) {
                registry.findAll();
                registry.findById(CapabilityId.of("concurrent.capability." + i));
            }
        });

        registrationThread.start();
        readerThread.start();
        registrationThread.join(TimeUnit.SECONDS.toMillis(10));
        readerThread.join(TimeUnit.SECONDS.toMillis(10));

        assertFalse(registrationThread.isAlive());
        assertFalse(readerThread.isAlive());
        assertEquals(capabilityCount, registry.findAll().size());
    }

    private static void awaitGate(CyclicBarrier gate) {
        try {
            gate.await(5, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new IllegalStateException("Concurrent test gate failed", failure);
        }
    }
}
