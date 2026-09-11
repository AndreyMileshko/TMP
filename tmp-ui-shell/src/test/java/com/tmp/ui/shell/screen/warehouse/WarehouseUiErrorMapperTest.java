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

    private static final class OptimisticLockException extends RuntimeException {
        OptimisticLockException(String message) {
            super(message);
        }
    }
}
