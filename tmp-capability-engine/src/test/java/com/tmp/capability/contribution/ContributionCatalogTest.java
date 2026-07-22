package com.tmp.capability.contribution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.PermissionDescriptor;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ContributionCatalogTest {

    private static PermissionDescriptor permission(String id) {
        return PermissionDescriptor.of(id, "Display " + id, "Description " + id);
    }

    @Test
    void addAndRetrieveSucceeds() {
        ContributionCatalog<PermissionDescriptor> catalog =
                new ContributionCatalog<>("permission", PermissionDescriptor::permissionId);
        CapabilityId owner = CapabilityId.of("owner.a");

        catalog.add(owner, permission("perm.a"));

        assertEquals(1, catalog.activeEntries().size());
        assertEquals("perm.a", catalog.activeEntries().get(0).permissionId());
        assertEquals(owner, catalog.ownerOf("perm.a").orElseThrow());
    }

    @Test
    void duplicateIdAcrossOwnersRejected() {
        ContributionCatalog<PermissionDescriptor> catalog =
                new ContributionCatalog<>("permission", PermissionDescriptor::permissionId);
        catalog.add(CapabilityId.of("owner.a"), permission("perm.shared"));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> catalog.add(CapabilityId.of("owner.b"), permission("perm.shared")));
        assertTrue(failure.getMessage().contains("perm.shared"));
        assertEquals(1, catalog.activeEntries().size());
        assertEquals(CapabilityId.of("owner.a"), catalog.ownerOf("perm.shared").orElseThrow());
    }

    @Test
    void ownerTrackingIsCorrect() {
        ContributionCatalog<PermissionDescriptor> catalog =
                new ContributionCatalog<>("permission", PermissionDescriptor::permissionId);
        catalog.add(CapabilityId.of("owner.a"), permission("perm.a"));
        catalog.add(CapabilityId.of("owner.b"), permission("perm.b"));

        assertEquals(CapabilityId.of("owner.a"), catalog.ownerOf("perm.a").orElseThrow());
        assertEquals(CapabilityId.of("owner.b"), catalog.ownerOf("perm.b").orElseThrow());
        assertTrue(catalog.ownerOf("perm.missing").isEmpty());
    }

    @Test
    void removeAllForOwnerRemovesOnlyTargetOwnersEntries() {
        ContributionCatalog<PermissionDescriptor> catalog =
                new ContributionCatalog<>("permission", PermissionDescriptor::permissionId);
        CapabilityId ownerA = CapabilityId.of("owner.a");
        CapabilityId ownerB = CapabilityId.of("owner.b");
        catalog.add(ownerA, permission("perm.a1"));
        catalog.add(ownerA, permission("perm.a2"));
        catalog.add(ownerB, permission("perm.b1"));

        catalog.removeAllForOwner(ownerA);

        List<PermissionDescriptor> remaining = catalog.activeEntries();
        assertEquals(1, remaining.size());
        assertEquals("perm.b1", remaining.get(0).permissionId());
        assertTrue(catalog.ownerOf("perm.a1").isEmpty());
        assertTrue(catalog.ownerOf("perm.a2").isEmpty());
        assertEquals(ownerB, catalog.ownerOf("perm.b1").orElseThrow());
    }

    @Test
    void concurrentAddAndReadProducesNoConcurrentModificationException() throws Exception {
        ContributionCatalog<PermissionDescriptor> catalog =
                new ContributionCatalog<>("permission", PermissionDescriptor::permissionId);
        int count = 200;
        CyclicBarrier gate = new CyclicBarrier(2);
        AtomicInteger writerFailures = new AtomicInteger();

        Thread writer = new Thread(() -> {
            awaitGate(gate);
            for (int i = 0; i < count; i++) {
                try {
                    catalog.add(CapabilityId.of("owner." + i), permission("perm." + i));
                } catch (RuntimeException failure) {
                    writerFailures.incrementAndGet();
                }
            }
        });

        Thread reader = new Thread(() -> {
            awaitGate(gate);
            for (int i = 0; i < count; i++) {
                catalog.activeEntries();
                catalog.ownerOf("perm." + i);
            }
        });

        writer.start();
        reader.start();
        writer.join(TimeUnit.SECONDS.toMillis(10));
        reader.join(TimeUnit.SECONDS.toMillis(10));

        assertFalse(writer.isAlive());
        assertFalse(reader.isAlive());
        assertEquals(0, writerFailures.get());
        assertEquals(count, catalog.activeEntries().size());
    }

    private static void awaitGate(CyclicBarrier gate) {
        try {
            gate.await(5, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new IllegalStateException("Concurrent test gate failed", failure);
        }
    }
}
