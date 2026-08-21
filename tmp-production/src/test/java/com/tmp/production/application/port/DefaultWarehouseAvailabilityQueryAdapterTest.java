package com.tmp.production.application.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tmp.warehouse.api.WarehouseApi.AvailabilityResult;
import com.tmp.warehouse.api.WarehouseApi.AvailabilityStatus;
import com.tmp.warehouse.api.WarehouseQueryApi;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * STAGE7-007 adapter contract: probe quantity is only used to call Warehouse Query; the returned
 * available quantity is {@link AvailabilityResult#availableQuantity()}, not the probe/requested
 * quantity.
 */
class DefaultWarehouseAvailabilityQueryAdapterTest {

    @Test
    void availableQuantityReturnsWarehouseAvailableQuantityNotProbe() {
        WarehouseQueryApi warehouseQuery = mock(WarehouseQueryApi.class);
        DefaultWarehouseAvailabilityQueryAdapter adapter =
                new DefaultWarehouseAvailabilityQueryAdapter(warehouseQuery);

        UUID materialId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        BigDecimal warehouseAvailable = new BigDecimal("42.5");

        when(warehouseQuery.checkAvailability(materialId, warehouseId, BigDecimal.ONE))
                .thenReturn(
                        new AvailabilityResult(
                                AvailabilityStatus.AVAILABLE,
                                "MAT-1",
                                BigDecimal.ONE,
                                warehouseAvailable));

        BigDecimal result = adapter.availableQuantity(materialId, warehouseId);

        assertEquals(0, warehouseAvailable.compareTo(result));
        assertEquals(0, BigDecimal.ONE.compareTo(BigDecimal.ONE));
        verify(warehouseQuery).checkAvailability(eq(materialId), eq(warehouseId), eq(BigDecimal.ONE));
    }
}
