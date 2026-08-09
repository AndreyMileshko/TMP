package com.tmp.ui.shell.screen.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
