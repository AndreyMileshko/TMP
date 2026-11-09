package com.tmp.reference.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.api.PermissionDescriptor;
import com.tmp.reference.api.ColorCatalogCommandApi;
import com.tmp.reference.api.ColorCatalogQueryApi;
import com.tmp.reference.api.ColorDto;
import com.tmp.reference.api.ColorImportResolutionApi;
import com.tmp.reference.api.ColorMatchingSettingApi;
import com.tmp.reference.api.ResolvedColorPair;
import com.tmp.reference.api.SourceColorResolution;
import com.tmp.reference.api.ImportMappingSaveReport;
import com.tmp.reference.api.So7ColorMappingCommandApi;
import com.tmp.reference.api.So7ColorMappingDto;
import com.tmp.reference.api.So7ColorMappingQueryApi;
import com.tmp.reference.capability.ReferenceDataCapability;
import com.tmp.security.api.PermissionId;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
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
        ColorCatalogQueryApi queryApi =
                new ColorCatalogQueryApi() {
                    @Override
                    public List<ColorDto> listColors(ColorDto.ActiveFilter filter, String search) {
                        return List.of();
                    }

                    @Override
                    public long getArchiveImpact(UUID colorId) {
                        return 0L;
                    }
                };
        ColorCatalogCommandApi commandApi =
                new ColorCatalogCommandApi() {
                    @Override
                    public ColorDto createColor(String code) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public int archiveColor(UUID colorId, long expectedVersion) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public ColorDto restoreColor(UUID colorId, long expectedVersion) {
                        throw new UnsupportedOperationException();
                    }
                };
        So7ColorMappingQueryApi mappingQueryApi = (filter, search) -> List.of();
        So7ColorMappingCommandApi mappingCommandApi =
                new So7ColorMappingCommandApi() {
                    @Override
                    public So7ColorMappingDto createMapping(
                            String sourceText, UUID insideColorId, UUID outsideColorId) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public So7ColorMappingDto updateMappingPair(
                            UUID mappingId,
                            UUID insideColorId,
                            UUID outsideColorId,
                            long expectedVersion) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public So7ColorMappingDto setMappingActive(
                            UUID mappingId, boolean active, long expectedVersion) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public void deleteMapping(UUID mappingId, long expectedVersion) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public ImportMappingSaveReport saveMappingsFromImport(
                            List<ImportMappingSaveReport.Candidate> candidates) {
                        throw new UnsupportedOperationException();
                    }
                };
        ColorMatchingSettingApi settingApi =
                new ColorMatchingSettingApi() {
                    @Override
                    public ColorMatchingSettingDto getAutomaticMatchingSetting() {
                        return new ColorMatchingSettingDto(false, 0L);
                    }

                    @Override
                    public ColorMatchingSettingDto setAutomaticMatchingEnabled(
                            boolean enabled, long expectedVersion) {
                        throw new UnsupportedOperationException();
                    }
                };
        ReferenceDataCapability capability =
                new ReferenceDataCapability(
                        queryApi, commandApi, mappingQueryApi, mappingCommandApi, settingApi,
                        new ColorImportResolutionApi() {
                            @Override
                            public boolean isAutomaticMatchingEnabled() {
                                return false;
                            }

                            @Override
                            public List<SourceColorResolution> resolveSourceTexts(
                                    List<String> sourceTexts) {
                                return List.of();
                            }

                            @Override
                            public List<ColorDto> listActiveColors() {
                                return List.of();
                            }

                            @Override
                            public ResolvedColorPair composePair(
                                    UUID insideColorId, UUID outsideColorId) {
                                throw new UnsupportedOperationException();
                            }

                            @Override
                            public boolean canSaveGlobalMappings() {
                                return false;
                            }
                        });
        assertEquals(ReferenceDataCapability.ID, capability.descriptor().id());
        assertEquals(4, capability.descriptor().permissions().size());
        assertTrue(capability.descriptor().navigationContributions().isEmpty());
        assertTrue(capability.descriptor().views().isEmpty());
        assertTrue(capability.descriptor().commands().isEmpty());
        assertEquals(6, capability.descriptor().publicServices().size());
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