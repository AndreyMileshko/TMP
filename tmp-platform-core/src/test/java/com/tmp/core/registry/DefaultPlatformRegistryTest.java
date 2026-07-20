package com.tmp.core.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.core.support.TestPlatformComponent;
import org.junit.jupiter.api.Test;

class DefaultPlatformRegistryTest {

    @Test
    void registersAndFindsComponents() {
        DefaultPlatformRegistry registry = new DefaultPlatformRegistry();
        TestPlatformComponent component = new TestPlatformComponent("core-test");

        registry.registerInternal(component);

        assertTrue(registry.findById("core-test").isPresent());
        assertEquals(1, registry.registeredComponents().size());
        assertEquals("core-test", registry.registeredComponents().getFirst().id());
    }

    @Test
    void rejectsDuplicateRegistration() {
        DefaultPlatformRegistry registry = new DefaultPlatformRegistry();
        registry.registerInternal(new TestPlatformComponent("dup"));

        assertThrows(IllegalStateException.class, () -> registry.registerInternal(new TestPlatformComponent("dup")));
    }
}
