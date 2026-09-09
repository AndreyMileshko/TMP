package com.tmp.production.application.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tmp.warehouse.api.WarehouseApi.MaterialReferenceView;
import com.tmp.warehouse.api.WarehouseReferenceQueryApi;
import com.tmp.warehouse.api.WarehouseReferenceQueryApi.WarehouseReferenceView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultWarehouseReferenceQueryAdapterTest {

    private WarehouseReferenceQueryApi warehouseReferences;
    private DefaultWarehouseReferenceQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        warehouseReferences = mock(WarehouseReferenceQueryApi.class);
        adapter = new DefaultWarehouseReferenceQueryAdapter(warehouseReferences);
    }

    @Test
    void getWarehouseMapsReferenceView() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseReferences.getWarehouseReference(warehouseId))
                .thenReturn(
                        Optional.of(
                                new WarehouseReferenceView(warehouseId, "PROD", "Production", true)));

        Optional<WarehouseReferenceQueryPort.WarehouseReferenceEntry> mapped =
                adapter.getWarehouse(warehouseId);

        assertTrue(mapped.isPresent());
        assertEquals(warehouseId, mapped.orElseThrow().warehouseId());
        assertTrue(mapped.orElseThrow().active());
        verify(warehouseReferences).getWarehouseReference(warehouseId);
    }

    @Test
    void findMaterialReferencesByIdentityMapsViews() {
        UUID materialId = UUID.randomUUID();
        when(warehouseReferences.findMaterialReferencesByIdentity("MAT", "WHITE", "PCS"))
                .thenReturn(
                        List.of(
                                new MaterialReferenceView(
                                        materialId, "MAT", "Name", "WHITE", "100", "PCS")));

        List<WarehouseReferenceQueryPort.MaterialReferenceEntry> mapped =
                adapter.findMaterialReferencesByIdentity("MAT", "WHITE", "PCS");

        assertEquals(1, mapped.size());
        assertEquals(materialId, mapped.getFirst().materialReferenceId());
        assertEquals("MAT", mapped.getFirst().article());
        verify(warehouseReferences).findMaterialReferencesByIdentity("MAT", "WHITE", "PCS");
    }
}
