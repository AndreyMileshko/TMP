package com.tmp.capability.contribution;

import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CommandDescriptor;
import com.tmp.capability.api.EventContribution;
import com.tmp.capability.api.NavigationContribution;
import com.tmp.capability.api.PermissionDescriptor;
import com.tmp.capability.api.SettingsContribution;
import com.tmp.capability.api.ViewDescriptor;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Objects;

/**
 * Aggregates the six internal contribution catalogs (permissions, commands, views,
 * navigation, settings, event descriptors) behind one cohesive component used by the
 * registration orchestrator. Document and public-service contributions are intentionally
 * absent: they are registered externally against Document Engine / Platform Core and are
 * handled by a separate orchestrator task.
 *
 * <p>{@link #registerInternalContributions(CapabilityDescriptor)} registers every
 * contribution declared by the descriptor; on any failure it rolls back all catalogs for
 * that owner via {@link #removeAllForOwner(CapabilityId)} before rethrowing, so no
 * partial contribution state is left behind.
 */
public final class CapabilityContributionCatalogs {

    private final ContributionCatalog<PermissionDescriptor> permissions =
            new ContributionCatalog<>("permission", PermissionDescriptor::permissionId);
    private final ContributionCatalog<CommandDescriptor> commands =
            new ContributionCatalog<>("command", CommandDescriptor::commandId);
    private final ContributionCatalog<ViewDescriptor> views =
            new ContributionCatalog<>("view", ViewDescriptor::viewId);
    private final ContributionCatalog<NavigationContribution> navigation =
            new ContributionCatalog<>("navigation", NavigationContribution::navigationId);
    private final ContributionCatalog<SettingsContribution> settings =
            new ContributionCatalog<>("settings", SettingsContribution::settingKey);
    private final ContributionCatalog<EventContribution> events =
            new ContributionCatalog<>("event", EventContribution::eventTypeId);

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "Catalog accessors intentionally expose the owned ContributionCatalog "
                    + "instances; callers use them for ownerOf queries and registration, not to replace "
                    + "the aggregate's fields.")
    public ContributionCatalog<PermissionDescriptor> permissions() {
        return permissions;
    }

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "Catalog accessors intentionally expose the owned ContributionCatalog "
                    + "instances; callers use them for ownerOf queries and registration, not to replace "
                    + "the aggregate's fields.")
    public ContributionCatalog<CommandDescriptor> commands() {
        return commands;
    }

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "Catalog accessors intentionally expose the owned ContributionCatalog "
                    + "instances; callers use them for ownerOf queries and registration, not to replace "
                    + "the aggregate's fields.")
    public ContributionCatalog<ViewDescriptor> views() {
        return views;
    }

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "Catalog accessors intentionally expose the owned ContributionCatalog "
                    + "instances; callers use them for ownerOf queries and registration, not to replace "
                    + "the aggregate's fields.")
    public ContributionCatalog<NavigationContribution> navigation() {
        return navigation;
    }

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "Catalog accessors intentionally expose the owned ContributionCatalog "
                    + "instances; callers use them for ownerOf queries and registration, not to replace "
                    + "the aggregate's fields.")
    public ContributionCatalog<SettingsContribution> settings() {
        return settings;
    }

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "Catalog accessors intentionally expose the owned ContributionCatalog "
                    + "instances; callers use them for ownerOf queries and registration, not to replace "
                    + "the aggregate's fields.")
    public ContributionCatalog<EventContribution> events() {
        return events;
    }

    public void registerInternalContributions(CapabilityDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        CapabilityId owner = descriptor.id();
        try {
            for (PermissionDescriptor permission : descriptor.permissions()) {
                permissions.add(owner, permission);
            }
            for (CommandDescriptor command : descriptor.commands()) {
                commands.add(owner, command);
            }
            for (ViewDescriptor view : descriptor.views()) {
                views.add(owner, view);
            }
            for (NavigationContribution nav : descriptor.navigationContributions()) {
                navigation.add(owner, nav);
            }
            for (SettingsContribution setting : descriptor.settings()) {
                settings.add(owner, setting);
            }
            for (EventContribution event : descriptor.events()) {
                events.add(owner, event);
            }
        } catch (RuntimeException failure) {
            removeAllForOwner(owner);
            throw failure;
        }
    }

    public void removeAllForOwner(CapabilityId owner) {
        Objects.requireNonNull(owner, "owner");
        permissions.removeAllForOwner(owner);
        commands.removeAllForOwner(owner);
        views.removeAllForOwner(owner);
        navigation.removeAllForOwner(owner);
        settings.removeAllForOwner(owner);
        events.removeAllForOwner(owner);
    }

    public List<PermissionDescriptor> activePermissions() {
        return permissions.activeEntries();
    }

    public List<CommandDescriptor> activeCommands() {
        return commands.activeEntries();
    }

    public List<ViewDescriptor> activeViews() {
        return views.activeEntries();
    }

    public List<NavigationContribution> activeNavigation() {
        return navigation.activeEntries();
    }

    public List<SettingsContribution> activeSettings() {
        return settings.activeEntries();
    }

    public List<EventContribution> activeEvents() {
        return events.activeEntries();
    }
}
