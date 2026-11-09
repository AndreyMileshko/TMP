package com.tmp.reference.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.api.PublicServiceContribution;
import com.tmp.reference.api.ColorCatalogCommandApi;
import com.tmp.reference.api.ColorCatalogQueryApi;
import com.tmp.reference.api.ColorDto;
import com.tmp.reference.api.ColorImportResolutionApi;
import com.tmp.reference.api.ColorMatchingSettingApi;
import com.tmp.reference.api.ImportMappingSaveReport;
import com.tmp.reference.api.ResolvedColorPair;
import com.tmp.reference.api.So7ColorMappingCommandApi;
import com.tmp.reference.api.So7ColorMappingDto;
import com.tmp.reference.api.So7ColorMappingQueryApi;
import com.tmp.reference.api.SourceColorResolution;
import com.tmp.reference.security.ReferenceDataPermissionCatalog;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReferenceDataCapabilityTest {

    @Test
    void descriptorExposesReferenceCapabilityWithPermissionsAndPublicServices() {
        ColorCatalogQueryApi colorQuery = stubColorQueryApi();
        ColorCatalogCommandApi colorCommand = stubColorCommandApi();
        So7ColorMappingQueryApi mappingQuery = stubMappingQueryApi();
        So7ColorMappingCommandApi mappingCommand = stubMappingCommandApi();
        ColorMatchingSettingApi settingApi = stubSettingApi();
        ColorImportResolutionApi resolutionApi = stubResolutionApi();
        ReferenceDataCapability capability =
                new ReferenceDataCapability(
                        colorQuery,
                        colorCommand,
                        mappingQuery,
                        mappingCommand,
                        settingApi,
                        resolutionApi);

        assertEquals(ReferenceDataCapability.ID, capability.descriptor().id());
        assertEquals("Reference Data", capability.descriptor().name());
        assertEquals(ReferenceDataCapability.VERSION, capability.descriptor().version());
        assertEquals(ReferenceDataPermissionCatalog.all(), capability.descriptor().permissions());
        assertTrue(capability.descriptor().commands().isEmpty());
        assertTrue(capability.descriptor().views().isEmpty());
        assertTrue(capability.descriptor().navigationContributions().isEmpty());

        List<PublicServiceContribution<?>> publicServices =
                capability.descriptor().publicServices();
        assertEquals(6, publicServices.size());
        assertEquals(ColorCatalogQueryApi.class, publicServices.get(0).serviceType());
        assertEquals(colorQuery, publicServices.get(0).serviceInstance());
        assertEquals(ColorCatalogCommandApi.class, publicServices.get(1).serviceType());
        assertEquals(colorCommand, publicServices.get(1).serviceInstance());
        assertEquals(So7ColorMappingQueryApi.class, publicServices.get(2).serviceType());
        assertEquals(mappingQuery, publicServices.get(2).serviceInstance());
        assertEquals(So7ColorMappingCommandApi.class, publicServices.get(3).serviceType());
        assertEquals(mappingCommand, publicServices.get(3).serviceInstance());
        assertEquals(ColorMatchingSettingApi.class, publicServices.get(4).serviceType());
        assertEquals(settingApi, publicServices.get(4).serviceInstance());
        assertEquals(ColorImportResolutionApi.class, publicServices.get(5).serviceType());
        assertEquals(resolutionApi, publicServices.get(5).serviceInstance());
    }

    private static ColorCatalogQueryApi stubColorQueryApi() {
        return new ColorCatalogQueryApi() {
            @Override
            public List<ColorDto> listColors(ColorDto.ActiveFilter filter, String search) {
                return List.of();
            }

            @Override
            public long getArchiveImpact(UUID colorId) {
                return 0L;
            }
        };
    }

    private static ColorCatalogCommandApi stubColorCommandApi() {
        return new ColorCatalogCommandApi() {
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
    }

    private static So7ColorMappingQueryApi stubMappingQueryApi() {
        return (filter, search) -> List.of();
    }

    private static So7ColorMappingCommandApi stubMappingCommandApi() {
        return new So7ColorMappingCommandApi() {
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
    }

    private static ColorMatchingSettingApi stubSettingApi() {
        return new ColorMatchingSettingApi() {
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
    }

    private static ColorImportResolutionApi stubResolutionApi() {
        return new ColorImportResolutionApi() {
            @Override
            public boolean isAutomaticMatchingEnabled() {
                return false;
            }

            @Override
            public List<SourceColorResolution> resolveSourceTexts(List<String> sourceTexts) {
                return List.of();
            }

            @Override
            public List<ColorDto> listActiveColors() {
                return List.of();
            }

            @Override
            public ResolvedColorPair composePair(UUID insideColorId, UUID outsideColorId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean canSaveGlobalMappings() {
                return false;
            }
        };
    }
}