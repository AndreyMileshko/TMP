package com.tmp.ui.shell.screen.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.warehouse.security.WarehouseCapability;
import org.junit.jupiter.api.Test;

class WarehouseNavigationCapabilityTest {

    @Test
    void operationsWarehouseNavIsAbsent() {
        WarehouseCapability capability = new WarehouseCapability();
        assertEquals(2, capability.descriptor().navigationContributions().size());
        assertTrue(
                capability.descriptor().navigationContributions().stream()
                        .anyMatch(nav -> "Склад".equals(nav.displayName())));
        assertTrue(
                capability.descriptor().navigationContributions().stream()
                        .anyMatch(nav -> "Настройки склада".equals(nav.displayName())));
        assertFalse(
                capability.descriptor().navigationContributions().stream()
                        .anyMatch(nav -> "Операции склада".equals(nav.displayName())));
        assertFalse(
                capability.descriptor().navigationContributions().stream()
                        .anyMatch(nav -> "warehouse.nav.operations".equals(nav.navigationId())));
        assertFalse(
                capability.descriptor().views().stream()
                        .anyMatch(view -> "warehouse.view.workbench".equals(view.viewId())));
    }
}
