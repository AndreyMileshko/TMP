package com.tmp.capability;

import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityEngine;
import com.tmp.capability.api.CapabilityEngineStatus;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityLifecycleState;
import com.tmp.capability.api.CommandDescriptor;
import com.tmp.capability.api.NavigationContribution;
import com.tmp.capability.api.PermissionDescriptor;
import com.tmp.capability.api.ViewDescriptor;
import com.tmp.capability.contribution.CapabilityContributionCatalogs;
import com.tmp.capability.discovery.CapabilityDiscovery;
import com.tmp.capability.lifecycle.CapabilityLifecycleManager;
import com.tmp.capability.registration.CapabilityRegistrationService;
import com.tmp.capability.registry.CapabilityRegistration;
import com.tmp.capability.registry.CapabilityRegistry;
import com.tmp.capability.validation.DependencyGraphValidator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default {@link CapabilityEngine} implementation wiring discovery, dependency
 * validation, registration, lifecycle management and contribution catalogs. Package
 * {@code com.tmp.capability} (not {@code .api}) — external callers must use the
 * {@link CapabilityEngine} interface only.
 */
public final class DefaultCapabilityEngine implements CapabilityEngine {

    private final CapabilityDiscovery discovery;
    private final CapabilityRegistrationService registrationService;
    private final CapabilityLifecycleManager lifecycleManager;
    private final CapabilityRegistry capabilityRegistry;
    private final CapabilityContributionCatalogs contributionCatalogs;
    private final AtomicInteger discoveredCount = new AtomicInteger();

    public DefaultCapabilityEngine(
            CapabilityDiscovery discovery,
            CapabilityRegistrationService registrationService,
            CapabilityLifecycleManager lifecycleManager,
            CapabilityRegistry capabilityRegistry,
            CapabilityContributionCatalogs contributionCatalogs) {
        this.discovery = Objects.requireNonNull(discovery, "discovery");
        this.registrationService = Objects.requireNonNull(registrationService, "registrationService");
        this.lifecycleManager = Objects.requireNonNull(lifecycleManager, "lifecycleManager");
        this.capabilityRegistry = Objects.requireNonNull(capabilityRegistry, "capabilityRegistry");
        this.contributionCatalogs = Objects.requireNonNull(contributionCatalogs, "contributionCatalogs");
    }

    @Override
    public void discoverAndRegisterAll() {
        List<Capability> discovered = discovery.discover();
        discoveredCount.set(discovered.size());
        List<Capability> ordered = DependencyGraphValidator.validate(discovered);
        for (Capability capability : ordered) {
            registrationService.register(capability);
        }
        lifecycleManager.initializeAll();
    }

    @Override
    public void activateAll() {
        lifecycleManager.activateAll();
    }

    @Override
    public void deactivate(CapabilityId id) {
        lifecycleManager.deactivate(id);
    }

    @Override
    public void stopAll() {
        lifecycleManager.stopAll();
    }

    @Override
    public Optional<CapabilityDescriptor> findById(CapabilityId id) {
        return capabilityRegistry.findById(id).map(CapabilityRegistration::descriptor);
    }

    @Override
    public List<CapabilityDescriptor> registeredCapabilities() {
        List<CapabilityDescriptor> descriptors = new ArrayList<>();
        for (CapabilityRegistration registration : capabilityRegistry.findAll()) {
            descriptors.add(registration.descriptor());
        }
        return List.copyOf(descriptors);
    }

    @Override
    public CapabilityLifecycleState stateOf(CapabilityId id) {
        return capabilityRegistry
                .findById(id)
                .map(CapabilityRegistration::state)
                .orElseThrow(() -> new IllegalStateException("Capability not registered: " + id));
    }

    @Override
    public List<PermissionDescriptor> activePermissions() {
        return filterByActiveOwner(
                contributionCatalogs.activePermissions(),
                contributionCatalogs.permissions()::ownerOf);
    }

    @Override
    public List<CommandDescriptor> activeCommands() {
        return filterByActiveOwner(
                contributionCatalogs.activeCommands(), contributionCatalogs.commands()::ownerOf);
    }

    @Override
    public List<ViewDescriptor> activeViews() {
        return filterByActiveOwner(contributionCatalogs.activeViews(), contributionCatalogs.views()::ownerOf);
    }

    @Override
    public List<NavigationContribution> activeNavigation() {
        return filterByActiveOwner(
                contributionCatalogs.activeNavigation(), contributionCatalogs.navigation()::ownerOf);
    }

    @Override
    public CapabilityEngineStatus status() {
        int registered = 0;
        int active = 0;
        int failed = 0;
        for (CapabilityRegistration registration : capabilityRegistry.findAll()) {
            registered++;
            if (registration.state() == CapabilityLifecycleState.ACTIVE) {
                active++;
            } else if (registration.state() == CapabilityLifecycleState.FAILED) {
                failed++;
            }
        }
        return new CapabilityEngineStatus(discoveredCount.get(), registered, active, failed);
    }

    private Set<CapabilityId> activeOwnerIds() {
        Set<CapabilityId> activeOwners = new HashSet<>();
        for (CapabilityRegistration registration : capabilityRegistry.findAll()) {
            if (registration.state() == CapabilityLifecycleState.ACTIVE) {
                activeOwners.add(registration.descriptor().id());
            }
        }
        return activeOwners;
    }

    private <T> List<T> filterByActiveOwner(
            List<T> entries, java.util.function.Function<String, Optional<CapabilityId>> ownerLookup) {
        Set<CapabilityId> activeOwners = activeOwnerIds();
        List<T> filtered = new ArrayList<>();
        for (T entry : entries) {
            String entryId = entryIdOf(entry);
            Optional<CapabilityId> owner = ownerLookup.apply(entryId);
            if (owner.isPresent() && activeOwners.contains(owner.get())) {
                filtered.add(entry);
            }
        }
        return List.copyOf(filtered);
    }

    private static String entryIdOf(Object entry) {
        if (entry instanceof PermissionDescriptor permission) {
            return permission.permissionId();
        }
        if (entry instanceof CommandDescriptor command) {
            return command.commandId();
        }
        if (entry instanceof ViewDescriptor view) {
            return view.viewId();
        }
        if (entry instanceof NavigationContribution navigation) {
            return navigation.navigationId();
        }
        throw new IllegalArgumentException("Unsupported contribution type: " + entry.getClass());
    }
}
