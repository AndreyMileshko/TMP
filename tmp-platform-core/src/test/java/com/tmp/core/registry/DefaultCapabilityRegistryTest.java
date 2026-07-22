package com.tmp.core.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.core.api.capability.CapabilityDescriptor;
import org.junit.jupiter.api.Test;

class DefaultCapabilityRegistryTest {

    @Test
    void unregisterRemovesCapabilityMetadata() {
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        CapabilityDescriptor descriptor = new CapabilityDescriptor("cap.test", "Test", "0.1.0");

        registry.register(descriptor);
        assertTrue(registry.findById("cap.test").isPresent());

        registry.unregister("cap.test");

        assertTrue(registry.findById("cap.test").isEmpty());
        assertEquals(0, registry.findAll().size());
    }

    @Test
    void registersCapabilityMetadata() {
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        CapabilityDescriptor descriptor = new CapabilityDescriptor("cap.test", "Test", "0.1.0");

        registry.register(descriptor);

        assertTrue(registry.findById("cap.test").isPresent());
        assertEquals(1, registry.findAll().size());
    }

    @Test
    void rejectsDuplicateCapability() {
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        registry.register(new CapabilityDescriptor("cap.dup", "Dup", "0.1.0"));

        assertThrows(
                IllegalStateException.class,
                () -> registry.register(new CapabilityDescriptor("cap.dup", "Dup", "0.1.0")));
    }
}
