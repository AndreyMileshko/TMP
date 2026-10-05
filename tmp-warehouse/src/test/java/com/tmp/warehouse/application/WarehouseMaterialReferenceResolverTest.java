package com.tmp.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.tmp.warehouse.domain.MaterialReference;
import java.util.List;
import org.junit.jupiter.api.Test;

class WarehouseMaterialReferenceResolverTest {

    private final WarehouseMaterialReferenceResolver resolver =
            new WarehouseMaterialReferenceResolver();

    @Test
    void unmatchedWhenCatalogEmpty() {
        var result = resolver.resolve("MAT-NEW", "WHITE", "шт.", List.of());
        assertEquals(WarehouseMaterialReferenceResolver.ResolutionStatus.UNMATCHED, result.status());
        assertNull(result.materialReferenceId());
    }

    @Test
    void resolvedWhenExactlyOneMatchIgnoringSize() {
        MaterialReference a = MaterialReference.create("MAT-1", "Name", "WHITE", "1500", "шт.");
        MaterialReference other = MaterialReference.create("MAT-2", "Other", "WHITE", "", "шт.");
        var result = resolver.resolve("MAT-1", "WHITE", "шт", List.of(a, other));
        assertEquals(WarehouseMaterialReferenceResolver.ResolutionStatus.RESOLVED, result.status());
        assertEquals(a.id().value(), result.materialReferenceId());
    }

    @Test
    void ambiguousWhenMultipleMatchesDifferentSize() {
        MaterialReference a = MaterialReference.create("MAT-1", "Name", "WHITE", "1500", "шт.");
        MaterialReference b = MaterialReference.create("MAT-1", "Name", "WHITE", "3000", "шт.");
        var result = resolver.resolve("MAT-1", "WHITE", "шт.", List.of(a, b));
        assertEquals(WarehouseMaterialReferenceResolver.ResolutionStatus.AMBIGUOUS, result.status());
        assertNull(result.materialReferenceId());
    }
}
