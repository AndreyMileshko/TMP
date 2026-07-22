package com.tmp.core.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.core.api.ServiceRegistration;
import com.tmp.core.api.component.ComponentType;
import com.tmp.core.api.component.PlatformComponentMetadata;
import org.junit.jupiter.api.Test;

class DefaultServiceRegistryTest {

    interface SampleService {
        String value();
    }

    interface AnotherService {
        int count();
    }

    @Test
    void unregisterRemovesServiceFromLookup() {
        DefaultServiceRegistry registry = new DefaultServiceRegistry();
        PlatformComponentMetadata owner = new PlatformComponentMetadata(
                "svc-owner", "Owner", "1.0.0", ComponentType.SERVICE);
        SampleService service = () -> "ok";

        ServiceRegistration registration =
                registry.register(SampleService.class, service, owner);
        assertTrue(registry.lookup(SampleService.class).isPresent());

        registration.unregister();

        assertTrue(registry.lookup(SampleService.class).isEmpty());
        assertEquals(0, registry.registeredServiceCount());
    }

    @Test
    void registersAndLooksUpServices() {
        DefaultServiceRegistry registry = new DefaultServiceRegistry();
        PlatformComponentMetadata owner = new PlatformComponentMetadata(
                "svc-owner", "Owner", "1.0.0", ComponentType.SERVICE);
        SampleService service = () -> "ok";

        registry.register(SampleService.class, service, owner);

        assertTrue(registry.lookup(SampleService.class).isPresent());
        assertEquals("ok", registry.lookup(SampleService.class).orElseThrow().value());
        assertEquals(1, registry.registeredServiceCount());
        assertEquals(1, registry.registeredServices().size());
    }

    @Test
    void lookupAllReturnsAllInstancesForType() {
        DefaultServiceRegistry registry = new DefaultServiceRegistry();
        PlatformComponentMetadata ownerA = new PlatformComponentMetadata(
                "owner-a", "Owner A", "1.0.0", ComponentType.SERVICE);
        PlatformComponentMetadata ownerB = new PlatformComponentMetadata(
                "owner-b", "Owner B", "1.0.0", ComponentType.SERVICE);

        registry.register(SampleService.class, () -> "first", ownerA);
        registry.register(SampleService.class, () -> "second", ownerB);

        assertEquals(2, registry.lookupAll(SampleService.class).size());
        assertEquals(2, registry.registeredServiceCount());
        assertEquals(2, registry.registeredServices().size());
    }

    @Test
    void tracksMultipleServiceTypesAndOwners() {
        DefaultServiceRegistry registry = new DefaultServiceRegistry();
        PlatformComponentMetadata owner = new PlatformComponentMetadata(
                "multi-owner", "Multi Owner", "1.0.0", ComponentType.SERVICE);

        registry.register(SampleService.class, () -> "sample", owner);
        registry.register(AnotherService.class, () -> 7, owner);

        assertEquals(2, registry.registeredServiceCount());
        assertEquals(2, registry.registeredServices().size());
        assertTrue(registry.lookup(SampleService.class).isPresent());
        assertTrue(registry.lookup(AnotherService.class).isPresent());
    }
}
