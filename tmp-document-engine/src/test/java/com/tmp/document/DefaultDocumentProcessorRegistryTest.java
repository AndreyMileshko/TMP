package com.tmp.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.document.support.TestDocumentProcessor;
import org.junit.jupiter.api.Test;

class DefaultDocumentProcessorRegistryTest {

    private static final String TYPE_ID = "processor.lifecycle";

    @Test
    void unregisterRemovesProcessorAndAllowsReRegistration() {
        DefaultDocumentProcessorRegistry registry = new DefaultDocumentProcessorRegistry();
        TestDocumentProcessor processor = new TestDocumentProcessor(TYPE_ID);

        registry.register(processor);
        assertTrue(registry.isRegistered(TYPE_ID));
        assertTrue(registry.isActive(TYPE_ID));

        registry.unregister(TYPE_ID);

        assertFalse(registry.isRegistered(TYPE_ID));
        assertFalse(registry.isActive(TYPE_ID));
        registry.register(processor);
        assertTrue(registry.isActive(TYPE_ID));
    }

    @Test
    void deactivateBlocksOperationsButPreservesTypeMetadataForExistingDocuments() {
        DefaultDocumentProcessorRegistry registry = new DefaultDocumentProcessorRegistry();
        TestDocumentProcessor processor = new TestDocumentProcessor(TYPE_ID);

        registry.register(processor);
        registry.deactivate(TYPE_ID);

        assertFalse(registry.isActive(TYPE_ID));
        assertThrows(IllegalStateException.class, () -> registry.require(TYPE_ID));
        assertEquals(0, registry.registeredTypes().size());
    }

    @Test
    void deactivateDiffersFromUnregisterByBlockingReRegistrationUntilExplicitRegister() {
        DefaultDocumentProcessorRegistry registry = new DefaultDocumentProcessorRegistry();
        TestDocumentProcessor processor = new TestDocumentProcessor(TYPE_ID);

        registry.register(processor);
        registry.deactivate(TYPE_ID);

        assertFalse(registry.isRegistered(TYPE_ID));
        registry.register(processor);
        assertTrue(registry.isActive(TYPE_ID));
    }
}
