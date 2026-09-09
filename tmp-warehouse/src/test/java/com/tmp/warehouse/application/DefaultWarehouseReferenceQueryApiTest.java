package com.tmp.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.warehouse.api.WarehouseApi.MaterialReferenceView;
import com.tmp.warehouse.api.WarehouseReferenceQueryApi;
import com.tmp.warehouse.api.WarehouseReferenceQueryApi.WarehouseReferenceView;
import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.testsupport.InMemoryMaterialReferenceRepository;
import com.tmp.warehouse.testsupport.InMemoryWarehouseCatalogRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultWarehouseReferenceQueryApiTest {

    private InMemoryWarehouseCatalogRepository warehouses;
    private InMemoryMaterialReferenceRepository materials;
    private WarehouseReferenceQueryApi api;

    @BeforeEach
    void setUp() {
        warehouses = new InMemoryWarehouseCatalogRepository();
        materials = new InMemoryMaterialReferenceRepository();
        api = new DefaultWarehouseReferenceQueryApi(warehouses, materials);
    }

    @Test
    void getWarehouseReferenceReturnsExistingWithoutRbac() {
        Warehouse warehouse =
                Warehouse.of(WarehouseId.generate(), "PROD", "Production", true);
        warehouses.save(warehouse);

        Optional<WarehouseReferenceView> found =
                api.getWarehouseReference(warehouse.id().value());

        assertTrue(found.isPresent());
        assertEquals(warehouse.id().value(), found.orElseThrow().warehouseId());
        assertTrue(found.orElseThrow().active());
    }

    @Test
    void getWarehouseReferenceEmptyWhenUnknown() {
        assertTrue(api.getWarehouseReference(UUID.randomUUID()).isEmpty());
    }

    @Test
    void findMaterialReferencesByIdentityMatchesArticleColorUnitIgnoringSize() {
        MaterialReference matchA =
                MaterialReference.create("MAT-1", "Name A", "WHITE", "1000", "шт.");
        MaterialReference matchB =
                MaterialReference.create("MAT-1", "Name B", "WHITE", "2000", "шт.");
        MaterialReference otherColor =
                MaterialReference.create("MAT-1", "Other", "BLACK", "", "шт.");
        materials.create(matchA);
        materials.create(matchB);
        materials.create(otherColor);

        List<MaterialReferenceView> found =
                api.findMaterialReferencesByIdentity("MAT-1", "WHITE", "шт.");

        assertEquals(2, found.size());
        assertTrue(
                found.stream()
                        .map(MaterialReferenceView::materialReferenceId)
                        .toList()
                        .containsAll(List.of(matchA.id().value(), matchB.id().value())));
    }

    @Test
    void findMaterialReferencesByIdentityReturnsEmptyWhenUnresolved() {
        materials.create(MaterialReference.create("MAT-X", "X", "WHITE", "", "шт."));

        assertTrue(api.findMaterialReferencesByIdentity("MISSING", "WHITE", "шт.").isEmpty());
    }
}
