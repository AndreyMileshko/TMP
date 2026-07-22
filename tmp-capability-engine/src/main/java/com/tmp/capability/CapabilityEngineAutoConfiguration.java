package com.tmp.capability;

import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityEngine;
import com.tmp.capability.contribution.CapabilityContributionCatalogs;
import com.tmp.capability.contribution.CapabilityExternalContributionRegistry;
import com.tmp.capability.discovery.CapabilityDiscovery;
import com.tmp.capability.lifecycle.CapabilityEventSubscriptionRegistry;
import com.tmp.capability.lifecycle.CapabilityLifecycleManager;
import com.tmp.capability.registration.CapabilityRegistrationService;
import com.tmp.capability.registry.CapabilityRegistry;
import com.tmp.core.api.PlatformCore;
import com.tmp.core.api.component.PlatformComponent;
import com.tmp.document.api.DocumentEngine;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter(
        name = {
            "com.tmp.core.PlatformCoreAutoConfiguration",
            "com.tmp.document.DocumentEngineAutoConfiguration"
        })
public class CapabilityEngineAutoConfiguration {

    @Bean
    CapabilityRegistry capabilityEngineRegistry() {
        return new CapabilityRegistry();
    }

    @Bean
    CapabilityContributionCatalogs capabilityContributionCatalogs() {
        return new CapabilityContributionCatalogs();
    }

    @Bean
    CapabilityDiscovery capabilityDiscovery(List<Capability> discoveredCapabilities) {
        return new CapabilityDiscovery(discoveredCapabilities);
    }

    @Bean
    CapabilityExternalContributionRegistry capabilityExternalContributionRegistry() {
        return new CapabilityExternalContributionRegistry();
    }

    @Bean
    CapabilityEventSubscriptionRegistry capabilityEventSubscriptionRegistry() {
        return new CapabilityEventSubscriptionRegistry();
    }

    @Bean
    CapabilityRegistrationService capabilityRegistrationService(
            CapabilityRegistry capabilityEngineRegistry,
            CapabilityContributionCatalogs contributionCatalogs,
            CapabilityExternalContributionRegistry externalContributions,
            CapabilityEventSubscriptionRegistry eventSubscriptions,
            PlatformCore platformCore,
            DocumentEngine documentEngine) {
        return new CapabilityRegistrationService(
                capabilityEngineRegistry,
                contributionCatalogs,
                externalContributions,
                eventSubscriptions,
                platformCore,
                documentEngine);
    }

    @Bean
    CapabilityLifecycleManager capabilityLifecycleManager(
            CapabilityRegistry capabilityEngineRegistry,
            CapabilityContributionCatalogs contributionCatalogs,
            CapabilityExternalContributionRegistry externalContributions,
            CapabilityEventSubscriptionRegistry eventSubscriptions,
            PlatformCore platformCore) {
        return new CapabilityLifecycleManager(
                capabilityEngineRegistry,
                contributionCatalogs,
                externalContributions,
                eventSubscriptions,
                platformCore);
    }

    @Bean
    CapabilityEngine capabilityEngine(
            CapabilityDiscovery discovery,
            CapabilityRegistrationService registrationService,
            CapabilityLifecycleManager lifecycleManager,
            CapabilityRegistry capabilityEngineRegistry,
            CapabilityContributionCatalogs contributionCatalogs) {
        return new DefaultCapabilityEngine(
                discovery, registrationService, lifecycleManager, capabilityEngineRegistry, contributionCatalogs);
    }

    @Bean
    CapabilityEnginePlatformComponent capabilityEnginePlatformComponent(CapabilityEngine capabilityEngine) {
        return new CapabilityEnginePlatformComponent(capabilityEngine);
    }

    @Bean
    CapabilityEnginePlatformRegistrar capabilityEnginePlatformRegistrar(
            PlatformCore platformCore, CapabilityEnginePlatformComponent capabilityEnginePlatformComponent) {
        return new CapabilityEnginePlatformRegistrar(platformCore, capabilityEnginePlatformComponent);
    }

    static final class CapabilityEnginePlatformRegistrar {

        private final PlatformCore platformCore;
        private final PlatformComponent capabilityEngineComponent;

        CapabilityEnginePlatformRegistrar(PlatformCore platformCore, PlatformComponent capabilityEngineComponent) {
            this.platformCore = platformCore;
            this.capabilityEngineComponent = capabilityEngineComponent;
        }

        @PostConstruct
        void registerCapabilityEngineComponent() {
            platformCore.registerComponent(capabilityEngineComponent);
        }
    }
}
