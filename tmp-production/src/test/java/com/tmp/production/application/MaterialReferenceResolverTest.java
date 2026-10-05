package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.tmp.production.application.MaterialReferenceResolver.CatalogEntry;
import com.tmp.production.application.MaterialReferenceResolver.ResolutionStatus;
import com.tmp.production.domain.SpecificationMaterialIdentity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MaterialReferenceResolverTest {

    private final MaterialReferenceResolver resolver = new MaterialReferenceResolver();

    @Test
    void matchesOmShtWithoutDotToWarehouseShtWithDot() {
        UUID id = UUID.randomUUID();
        SpecificationMaterialIdentity identity =
                SpecificationMaterialIdentity.of("MAT-001", "White", "шт");
        List<CatalogEntry> catalog =
                List.of(new CatalogEntry(id, "MAT-001", "White", "шт."));

        var result = resolver.resolve(identity, catalog);

        assertEquals(ResolutionStatus.RESOLVED, result.status());
        assertEquals(id, result.materialReferenceId());
    }

    @Test
    void matchesSpacedUppercaseShtToCanonicalWarehouseUom() {
        UUID id = UUID.randomUUID();
        SpecificationMaterialIdentity identity =
                SpecificationMaterialIdentity.of("MAT-001", "White", " ШТ ");
        List<CatalogEntry> catalog =
                List.of(new CatalogEntry(id, "MAT-001", "White", "шт."));

        var result = resolver.resolve(identity, catalog);

        assertEquals(ResolutionStatus.RESOLVED, result.status());
        assertEquals(id, result.materialReferenceId());
    }

    @Test
    void differentSemanticUnitsDoNotMatch() {
        UUID id = UUID.randomUUID();
        SpecificationMaterialIdentity identity =
                SpecificationMaterialIdentity.of("MAT-001", "White", "м");
        List<CatalogEntry> catalog =
                List.of(new CatalogEntry(id, "MAT-001", "White", "шт."));

        var result = resolver.resolve(identity, catalog);

        assertEquals(ResolutionStatus.UNRESOLVED, result.status());
        assertNull(result.materialReferenceId());
    }

    @Test
    void ambiguousWhenMultipleReferencesShareCanonicalIdentity() {
        SpecificationMaterialIdentity identity =
                SpecificationMaterialIdentity.of("MAT-001", "White", "шт");
        List<CatalogEntry> catalog =
                List.of(
                        new CatalogEntry(UUID.randomUUID(), "MAT-001", "White", "шт."),
                        new CatalogEntry(UUID.randomUUID(), "MAT-001", "White", " шт. "));

        var result = resolver.resolve(identity, catalog);

        assertEquals(ResolutionStatus.AMBIGUOUS, result.status());
        assertNull(result.materialReferenceId());
    }

    @Test
    void ambiguousWhenSameArticleColorCanonicalUomDifferentUnderlyingEntries() {
        SpecificationMaterialIdentity identity =
                SpecificationMaterialIdentity.of("MAT-001", "White", "шт.");
        List<CatalogEntry> catalog =
                List.of(
                        new CatalogEntry(UUID.randomUUID(), "MAT-001", "White", "шт"),
                        new CatalogEntry(UUID.randomUUID(), "MAT-001", "White", "шт."));

        var result = resolver.resolve(identity, catalog);

        assertEquals(ResolutionStatus.AMBIGUOUS, result.status());
        assertNull(result.materialReferenceId());
    }
}
