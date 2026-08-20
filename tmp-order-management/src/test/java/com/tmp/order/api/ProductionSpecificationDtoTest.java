package com.tmp.order.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductionSpecificationDtoTest {

    @Test
    void createsWithAllFields() {
        SpecificationId specId = SpecificationId.of(UUID.randomUUID());
        OrderItemId itemId = OrderItemId.generate();
        SpecificationLineDto line =
                SpecificationLineDto.of("MAT-01", "Steel", null, null, BigDecimal.TEN, "m");

        ProductionSpecificationDto dto =
                ProductionSpecificationDto.of(specId, itemId, BigDecimal.valueOf(5), List.of(line));

        assertEquals(specId, dto.specificationId());
        assertEquals(itemId, dto.orderItemId());
        assertEquals(BigDecimal.valueOf(5), dto.orderedQuantity());
        assertEquals(1, dto.lines().size());
        assertEquals("MAT-01", dto.lines().get(0).materialCode());
    }

    @Test
    void noRevisionNumberExposed() {
        ProductionSpecificationDto dto =
                ProductionSpecificationDto.of(
                        SpecificationId.of(UUID.randomUUID()),
                        OrderItemId.generate(),
                        BigDecimal.ONE,
                        List.of());
        assertNotNull(dto.specificationId());
        assertNotNull(dto.orderItemId());
    }

    @Test
    void nullSpecificationIdRejected() {
        assertThrows(
                NullPointerException.class,
                () ->
                        ProductionSpecificationDto.of(
                                null, OrderItemId.generate(), BigDecimal.ONE, List.of()));
    }
}
