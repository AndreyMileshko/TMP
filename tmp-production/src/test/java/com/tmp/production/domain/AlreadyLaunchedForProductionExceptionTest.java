package com.tmp.production.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AlreadyLaunchedForProductionExceptionTest {

    @Test
    void messageContainsIdentifiers() {
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();

        AlreadyLaunchedForProductionException ex =
                new AlreadyLaunchedForProductionException(itemId, specId);

        assertTrue(ex.getMessage().contains(itemId.toString()));
        assertTrue(ex.getMessage().contains(specId.toString()));
        assertEquals(itemId, ex.sourceOrderItemId());
        assertEquals(specId, ex.specificationId());
    }
}
