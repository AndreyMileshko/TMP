package com.tmp.production.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.api.PublicServiceContribution;
import com.tmp.capability.contribution.CapabilityContributionCatalogs;
import com.tmp.capability.contribution.CapabilityExternalContributionRegistry;
import com.tmp.capability.lifecycle.CapabilityEventSubscriptionRegistry;
import com.tmp.capability.registration.CapabilityRegistrationException;
import com.tmp.capability.registration.CapabilityRegistrationService;
import com.tmp.capability.registry.CapabilityRegistry;
import com.tmp.core.api.PlatformCore;
import com.tmp.core.api.ServiceRegistry;
import com.tmp.core.registry.DefaultCapabilityRegistry;
import com.tmp.core.registry.DefaultServiceRegistry;
import com.tmp.document.api.DocumentEngine;
import com.tmp.production.api.ProductionQueryApi;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ProductionQueryApiCapabilityRegistrationTest {

    @Test
    void capabilityEngineRegistrationPutsExactlyOneProductionQueryApiInServiceRegistry() {
        CapabilityRegistry capabilityRegistry = new CapabilityRegistry();
        CapabilityContributionCatalogs catalogs = new CapabilityContributionCatalogs();
        DefaultCapabilityRegistry platformCapabilityRegistry = new DefaultCapabilityRegistry();
        DefaultServiceRegistry serviceRegistry = new DefaultServiceRegistry();
        PlatformCore platformCore = Mockito.mock(PlatformCore.class);
        Mockito.when(platformCore.capabilityRegistry()).thenReturn(platformCapabilityRegistry);
        Mockito.when(platformCore.serviceRegistry()).thenReturn(serviceRegistry);

        CapabilityRegistrationService registrationService =
                new CapabilityRegistrationService(
                        capabilityRegistry,
                        catalogs,
                        new CapabilityExternalContributionRegistry(),
                        new CapabilityEventSubscriptionRegistry(),
                        platformCore,
                        Mockito.mock(DocumentEngine.class));

        ProductionQueryApi api = productionQueryApiStub();
        ProductionCapability capability = new ProductionCapability(api);

        List<PublicServiceContribution<?>> contributions = capability.descriptor().publicServices();
        assertEquals(1, contributions.size());
        assertEquals(ProductionQueryApi.class, contributions.getFirst().serviceType());
        assertSame(api, contributions.getFirst().serviceInstance());

        registrationService.register(capability);

        ServiceRegistry registry = platformCore.serviceRegistry();
        Optional<ProductionQueryApi> lookedUp = registry.lookup(ProductionQueryApi.class);
        assertTrue(lookedUp.isPresent());
        assertSame(api, lookedUp.orElseThrow());
        assertEquals(1, registry.lookupAll(ProductionQueryApi.class).size());
        assertEquals("production", registry.registeredServices().getFirst().id());
        assertEquals(1, registry.registeredServiceCount());

        CapabilityRegistrationException duplicate =
                assertThrows(
                        CapabilityRegistrationException.class,
                        () -> registrationService.register(new ProductionCapability(productionQueryApiStub())));
        assertTrue(duplicate.getMessage().contains("already registered"));
        assertEquals(1, registry.lookupAll(ProductionQueryApi.class).size());
        assertSame(api, registry.lookup(ProductionQueryApi.class).orElseThrow());
    }

    @Test
    void productionQueryApiDeclaresOnlyReadOperations() {
        for (Method method : ProductionQueryApi.class.getDeclaredMethods()) {
            if (method.isSynthetic()) {
                continue;
            }
            String name = method.getName().toLowerCase(Locale.ROOT);
            assertTrue(
                    name.equals("getorderproductionview")
                            || name.equals("getitemproductionstate")
                            || name.equals("getmaterialavailabilityresult")
                            || name.equals("listproductionhistory"),
                    "Unexpected Public Query API method: " + method);
            for (String forbidden :
                    List.of(
                            "accept",
                            "launch",
                            "create",
                            "update",
                            "delete",
                            "post",
                            "release",
                            "cancel",
                            "confirm")) {
                assertTrue(
                        !name.contains(forbidden),
                        "Public Query API must not expose mutating method: " + method);
            }
        }
        assertEquals(4, ProductionQueryApi.class.getDeclaredMethods().length);
    }

    private static ProductionQueryApi productionQueryApiStub() {
        return new ProductionQueryApi() {
            @Override
            public ProductionQueryApi.OrderProductionView getOrderProductionView(UUID orderId) {
                return null;
            }

            @Override
            public Optional<ProductionQueryApi.ItemProductionStateView> getItemProductionState(
                    UUID orderItemId) {
                return Optional.empty();
            }

            @Override
            public Optional<ProductionQueryApi.MaterialAvailabilityResultView> getMaterialAvailabilityResult(
                    UUID orderId) {
                return Optional.empty();
            }

            @Override
            public List<ProductionQueryApi.ProductionHistoryEntryView> listProductionHistory(UUID orderId) {
                return List.of();
            }
        };
    }
}
