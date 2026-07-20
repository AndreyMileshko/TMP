package com.tmp.core.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.core.api.component.ComponentType;
import com.tmp.core.api.component.PlatformComponentMetadata;
import org.junit.jupiter.api.Test;

class DefaultServiceRegistryTest {

    interface SampleService {
        String value();
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
        assertEquals(1, registry.registeredServices().size());
    }
}
