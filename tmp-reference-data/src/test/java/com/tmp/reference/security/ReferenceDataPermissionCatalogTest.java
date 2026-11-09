package com.tmp.reference.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.api.PermissionDescriptor;
import com.tmp.reference.capability.ReferenceDataCapability;
import com.tmp.security.api.PermissionId;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ReferenceDataPermissionCatalogTest {

    @Test
    void registersExactlyFourPermissionsWithoutDuplicates() {
        List<PermissionId> permissions = ReferenceDataPermissions.all();
        assertEquals(4, permissions.size());
        assertEquals(4, new HashSet<>(permissions).size());

        List<PermissionDescriptor> descriptors = ReferenceDataPermissionCatalog.all();
        assertEquals(4, descriptors.size());
        assertEquals(
                4,
                descriptors.stream()
                        .map(PermissionDescriptor::permissionId)
                        .collect(Collectors.toSet())
                        .size());
    }

    @Test
    void allPermissionIdsAreValidThreeSegmentCodes() {
        for (PermissionId permissionId : ReferenceDataPermissions.all()) {
            assertEquals(permissionId, PermissionId.of(permissionId.value()));
            assertEquals(3, permissionId.value().split("\\.").length);
        }
    }

    @Test
    void logicalPermissionsMapToExpectedCodes() {
        assertEquals("reference.color.view", ReferenceDataPermissions.COLOR_VIEW.value());
        assertEquals("reference.color.edit", ReferenceDataPermissions.COLOR_EDIT.value());
        assertEquals(
                "reference.color-mapping.edit",
                ReferenceDataPermissions.COLOR_MAPPING_EDIT.value());
        assertEquals(
                "reference.color-setting.edit",
                ReferenceDataPermissions.COLOR_SETTING_EDIT.value());
    }

    @Test
    void catalogContainsOnlyReferencePermissions() {
        assertTrue(ReferenceDataPermissionCatalog.containsCode("reference.color.view"));
        assertTrue(ReferenceDataPermissionCatalog.containsCode("reference.color.edit"));
        assertTrue(ReferenceDataPermissionCatalog.containsCode("reference.color-mapping.edit"));
        assertTrue(ReferenceDataPermissionCatalog.containsCode("reference.color-setting.edit"));
        assertFalse(ReferenceDataPermissionCatalog.containsCode("reference.color_mapping.edit"));
        assertFalse(ReferenceDataPermissionCatalog.containsCode("order.order.view"));
        assertFalse(ReferenceDataPermissionCatalog.containsCode("warehouse.stock.view"));
        assertFalse(ReferenceDataPermissionCatalog.containsCode("security.users.view"));
    }

    @Test
    void capabilityDescriptorExposesPermissionCatalogWithoutNavigation() {
        ReferenceDataCapability capability = new ReferenceDataCapability();
        assertEquals(ReferenceDataCapability.ID, capability.descriptor().id());
        assertEquals(4, capability.descriptor().permissions().size());
        assertTrue(capability.descriptor().navigationContributions().isEmpty());
        assertTrue(capability.descriptor().views().isEmpty());
        assertTrue(capability.descriptor().commands().isEmpty());
        assertTrue(capability.descriptor().publicServices().isEmpty());
        assertEquals(
                ReferenceDataPermissions.all().stream()
                        .map(PermissionId::value)
                        .collect(Collectors.toSet()),
                capability.descriptor().permissions().stream()
                        .map(PermissionDescriptor::permissionId)
                        .collect(Collectors.toSet()));
    }

    @Test
    void displayNamesMatchSpecificationIntents() {
        var byId =
                ReferenceDataPermissionCatalog.all().stream()
                        .collect(
                                Collectors.toMap(
                                        PermissionDescriptor::permissionId,
                                        PermissionDescriptor::displayName));

        assertEquals("Просмотр справочника цветов", byId.get("reference.color.view"));
        assertEquals("Изменение цветов TMP", byId.get("reference.color.edit"));
        assertEquals("Изменение соответствий СО7", byId.get("reference.color-mapping.edit"));
        assertEquals(
                "Изменение настройки сопоставления", byId.get("reference.color-setting.edit"));
    }
}
