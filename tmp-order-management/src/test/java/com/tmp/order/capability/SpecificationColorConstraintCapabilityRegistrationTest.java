package com.tmp.order.capability;

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
import com.tmp.order.api.imports.SpecificationColorConstraint;
import com.tmp.order.application.imports.DefaultSpecificationColorConstraint;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SpecificationColorConstraintCapabilityRegistrationTest {

    @Test
    void capabilityRegistersExactlyOneSpecificationColorConstraintPublicService() {
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

        SpecificationColorConstraint api = new DefaultSpecificationColorConstraint();
        OrderManagementCapability capability = new OrderManagementCapability(api);

        List<PublicServiceContribution<?>> contributions = capability.descriptor().publicServices();
        assertEquals(1, contributions.size());
        assertEquals(SpecificationColorConstraint.class, contributions.getFirst().serviceType());
        assertSame(api, contributions.getFirst().serviceInstance());

        registrationService.register(capability);

        ServiceRegistry registry = platformCore.serviceRegistry();
        Optional<SpecificationColorConstraint> lookedUp =
                registry.lookup(SpecificationColorConstraint.class);
        assertTrue(lookedUp.isPresent());
        assertSame(api, lookedUp.orElseThrow());
        assertEquals(1, registry.lookupAll(SpecificationColorConstraint.class).size());

        CapabilityRegistrationException duplicate =
                assertThrows(
                        CapabilityRegistrationException.class,
                        () ->
                                registrationService.register(
                                        new OrderManagementCapability(
                                                new DefaultSpecificationColorConstraint())));
        assertTrue(duplicate.getMessage().contains("already registered"));
        assertEquals(1, registry.lookupAll(SpecificationColorConstraint.class).size());
        assertSame(api, registry.lookup(SpecificationColorConstraint.class).orElseThrow());
    }
}