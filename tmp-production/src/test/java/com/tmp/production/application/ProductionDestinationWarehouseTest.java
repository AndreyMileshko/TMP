package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.domain.InvalidProductionDestinationWarehouseException;
import com.tmp.warehouse.api.WarehouseReferenceQueryApi;
import com.tmp.warehouse.api.WarehouseReferenceQueryApi.WarehouseReferenceView;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ProductionDestinationWarehouseTest {

    private static final UUID A = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID B = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");

    @Test
    void noneAssignedThrowsFriendlyMessage() {
        WarehouseReferenceQueryApi api = Mockito.mock(WarehouseReferenceQueryApi.class);
        Mockito.when(api.findProductionWarehouse()).thenReturn(Optional.empty());
        ProductionDestinationWarehouse destination =
                ProductionDestinationWarehouse.fromWarehouseReferences(api);

        assertTrue(destination.findProductionWarehouseId().isEmpty());
        InvalidProductionDestinationWarehouseException ex =
                assertThrows(
                        InvalidProductionDestinationWarehouseException.class,
                        destination::productionWarehouseId);
        assertEquals(InvalidProductionDestinationWarehouseException.NOT_ASSIGNED_MESSAGE, ex.getMessage());
    }

    @Test
    void resolvesAssignedWarehouseFromPublicApi() {
        WarehouseReferenceQueryApi api = Mockito.mock(WarehouseReferenceQueryApi.class);
        Mockito.when(api.findProductionWarehouse())
                .thenReturn(Optional.of(new WarehouseReferenceView(A, "SECOND", "Второй", true)));
        ProductionDestinationWarehouse destination =
                ProductionDestinationWarehouse.fromWarehouseReferences(api);
        assertEquals(A, destination.productionWarehouseId());
    }

    @Test
    void fixedBeanStillSupportsTests() {
        ProductionDestinationWarehouse destination = new ProductionDestinationWarehouse(B);
        assertEquals(B, destination.productionWarehouseId());
    }
}
