package com.tmp.reference.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.api.PublicServiceContribution;
import com.tmp.reference.api.ColorCatalogCommandApi;
import com.tmp.reference.api.ColorCatalogQueryApi;
import com.tmp.reference.api.ColorDto;
import com.tmp.reference.security.ReferenceDataPermissionCatalog;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReferenceDataCapabilityTest {

    @Test
    void descriptorExposesReferenceCapabilityWithPermissionsAndColorCatalogPublicServices() {
        ColorCatalogQueryApi queryApi = stubQueryApi();
        ColorCatalogCommandApi commandApi = stubCommandApi();
        ReferenceDataCapability capability = new ReferenceDataCapability(queryApi, commandApi);

        assertEquals(ReferenceDataCapability.ID, capability.descriptor().id());
        assertEquals("Reference Data", capability.descriptor().name());
        assertEquals(ReferenceDataCapability.VERSION, capability.descriptor().version());
        assertEquals(
                ReferenceDataPermissionCatalog.all(), capability.descriptor().permissions());
        assertTrue(capability.descriptor().commands().isEmpty());
        assertTrue(capability.descriptor().views().isEmpty());
        assertTrue(capability.descriptor().navigationContributions().isEmpty());

        List<PublicServiceContribution<?>> publicServices =
                capability.descriptor().publicServices();
        assertEquals(2, publicServices.size());
        assertEquals(ColorCatalogQueryApi.class, publicServices.get(0).serviceType());
        assertEquals(queryApi, publicServices.get(0).serviceInstance());
        assertEquals(ColorCatalogCommandApi.class, publicServices.get(1).serviceType());
        assertEquals(commandApi, publicServices.get(1).serviceInstance());
    }

    private static ColorCatalogQueryApi stubQueryApi() {
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

    private static ColorCatalogCommandApi stubCommandApi() {
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
}
