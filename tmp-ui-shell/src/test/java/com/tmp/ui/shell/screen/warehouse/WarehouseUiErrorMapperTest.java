package com.tmp.ui.shell.screen.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AccessDeniedException;
import org.junit.jupiter.api.Test;

class WarehouseUiErrorMapperTest {

    @Test
    void mapsAccessDenied() {
        assertEquals(
                WarehouseUiErrorMapper.ACCESS_DENIED,
                WarehouseUiErrorMapper.text(new AccessDeniedException("x")));
    }

    @Test
    void mapsValidation() {
        assertEquals(
                WarehouseUiErrorMapper.VALIDATION,
                WarehouseUiErrorMapper.text(new IllegalArgumentException("quantity must not be blank")));
    }

    @Test
    void mapsStaleOperationalRevision() {
        assertEquals(
                WarehouseUiErrorMapper.STALE_STATE,
                WarehouseUiErrorMapper.text(
                        new IllegalStateException("stale operational revision")));
    }

    @Test
    void mapsOptimisticLockSimpleName() {
        assertEquals(
                WarehouseUiErrorMapper.STALE_STATE,
                WarehouseUiErrorMapper.text(new OptimisticLockException("conflict")));
    }

    @Test
    void isStaleConflictDetectsPayloadRevision() {
        assertTrue(
                WarehouseUiErrorMapper.isStaleConflict(
                        new IllegalStateException("payload revision mismatch")));
        assertFalse(WarehouseUiErrorMapper.isStaleConflict(new IllegalArgumentException("required")));
    }

    @Test
    void mapsInsufficientStockFromInvalidWarehouseStateWithQuantities() {
        String mapped =
                WarehouseUiErrorMapper.text(
                        new InvalidWarehouseStateException(
                                "Insufficient stock for transfer send: available=0.000000, requested=1.000000"));
        assertEquals(
                "Недостаточно материала на складе. Доступно: 0. Запрошено: 1.", mapped);
        assertFalse(mapped.toLowerCase().contains("insufficient"));
        assertFalse(mapped.contains("шт."));
    }

    @Test
    void mapsInsufficientStockDecimalQuantitiesWithRuSeparator() {
        String mapped =
                WarehouseUiErrorMapper.text(
                        new InvalidWarehouseStateException(
                                "Insufficient IN_TRANSIT stock for transfer receive: available=0.750000, requested=1.250000"));
        assertEquals(
                "Недостаточно материала на складе. Доступно: 0,75. Запрошено: 1,25.", mapped);
    }

    @Test
    void mapsInsufficientStockWithoutQuantitiesToGenericRussian() {
        assertEquals(
                WarehouseUiErrorMapper.INSUFFICIENT_STOCK,
                WarehouseUiErrorMapper.text(
                        new InvalidWarehouseStateException(
                                "Insufficient stock for transfer send: no AVAILABLE position at cell=abc")));
    }

    @Test
    void mapsInsufficientStockWrappedInCauseChain() {
        RuntimeException wrapped =
                new RuntimeException(
                        "wrapper",
                        new InvalidWarehouseStateException(
                                "Insufficient stock for transfer send: available=2.000000, requested=5.000000"));
        assertEquals(
                "Недостаточно материала на складе. Доступно: 2. Запрошено: 5.",
                WarehouseUiErrorMapper.text(wrapped));
    }

    @Test
    void preservesRussianDomainInvalidWarehouseStateMessage() {
        String russian = "Склад производства недоступен для операции.";
        assertEquals(
                russian,
                WarehouseUiErrorMapper.text(new InvalidWarehouseStateException(russian)));
    }

    @Test
    void unrelatedNotFoundMappingUnchanged() {
        assertEquals(
                WarehouseUiErrorMapper.NOT_FOUND,
                WarehouseUiErrorMapper.text(new IllegalStateException("document not found")));
    }

    private static final class OptimisticLockException extends RuntimeException {
        OptimisticLockException(String message) {
            super(message);
        }
    }

    private static final class InvalidWarehouseStateException extends RuntimeException {
        InvalidWarehouseStateException(String message) {
            super(message);
        }
    }
}
