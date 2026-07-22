package com.tmp.capability.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Immutable aggregate describing everything a Capability contributes to the platform:
 * its identity and version, its declared dependencies, and every contribution type
 * (permissions, commands, views, navigation, documents, public services, events,
 * settings). Built exclusively via {@link Builder}.
 *
 * <p>This type enforces only descriptor-level self-consistency (no duplicate
 * contribution id within the same descriptor). Cross-capability uniqueness, dependency
 * graph validation, and lifecycle management are the responsibility of the Capability
 * Registry and related components, not of this pure data aggregate.
 */
public final class CapabilityDescriptor {

    private final CapabilityId id;
    private final String name;
    private final CapabilityVersion version;
    private final String description;
    private final List<DependencyDescriptor> dependencies;
    private final List<PermissionDescriptor> permissions;
    private final List<CommandDescriptor> commands;
    private final List<ViewDescriptor> views;
    private final List<NavigationContribution> navigationContributions;
    private final List<DocumentContribution> documents;
    private final List<PublicServiceContribution<?>> publicServices;
    private final List<EventContribution> events;
    private final List<SettingsContribution> settings;

    private CapabilityDescriptor(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.version = builder.version;
        this.description = builder.description;
        this.dependencies = List.copyOf(builder.dependencies);
        this.permissions = List.copyOf(builder.permissions);
        this.commands = List.copyOf(builder.commands);
        this.views = List.copyOf(builder.views);
        this.navigationContributions = List.copyOf(builder.navigationContributions);
        this.documents = List.copyOf(builder.documents);
        this.publicServices = List.copyOf(builder.publicServices);
        this.events = List.copyOf(builder.events);
        this.settings = List.copyOf(builder.settings);
    }

    public static Builder builder() {
        return new Builder();
    }

    public CapabilityId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public CapabilityVersion version() {
        return version;
    }

    public String description() {
        return description;
    }

    public List<DependencyDescriptor> dependencies() {
        return dependencies;
    }

    public List<PermissionDescriptor> permissions() {
        return permissions;
    }

    public List<CommandDescriptor> commands() {
        return commands;
    }

    public List<ViewDescriptor> views() {
        return views;
    }

    public List<NavigationContribution> navigationContributions() {
        return navigationContributions;
    }

    public List<DocumentContribution> documents() {
        return documents;
    }

    public List<PublicServiceContribution<?>> publicServices() {
        return publicServices;
    }

    public List<EventContribution> events() {
        return events;
    }

    public List<SettingsContribution> settings() {
        return settings;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CapabilityDescriptor that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id + "@" + version;
    }

    /** Thrown when a descriptor being built contains a duplicate contribution id. */
    public static final class DuplicateContributionException extends IllegalArgumentException {

        DuplicateContributionException(String message) {
            super(message);
        }
    }

    /** Builder for {@link CapabilityDescriptor}; performs all validation in {@link #build()}. */
    public static final class Builder {

        private CapabilityId id;
        private String name;
        private CapabilityVersion version;
        private String description;
        private List<DependencyDescriptor> dependencies = List.of();
        private List<PermissionDescriptor> permissions = List.of();
        private List<CommandDescriptor> commands = List.of();
        private List<ViewDescriptor> views = List.of();
        private List<NavigationContribution> navigationContributions = List.of();
        private List<DocumentContribution> documents = List.of();
        private List<PublicServiceContribution<?>> publicServices = List.of();
        private List<EventContribution> events = List.of();
        private List<SettingsContribution> settings = List.of();

        private Builder() {
        }

        public Builder id(CapabilityId id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder version(CapabilityVersion version) {
            this.version = version;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder dependencies(List<DependencyDescriptor> dependencies) {
            this.dependencies = new ArrayList<>(Objects.requireNonNull(dependencies, "dependencies"));
            return this;
        }

        public Builder permissions(List<PermissionDescriptor> permissions) {
            this.permissions = new ArrayList<>(Objects.requireNonNull(permissions, "permissions"));
            return this;
        }

        public Builder commands(List<CommandDescriptor> commands) {
            this.commands = new ArrayList<>(Objects.requireNonNull(commands, "commands"));
            return this;
        }

        public Builder views(List<ViewDescriptor> views) {
            this.views = new ArrayList<>(Objects.requireNonNull(views, "views"));
            return this;
        }

        public Builder navigationContributions(List<NavigationContribution> navigationContributions) {
            this.navigationContributions =
                    new ArrayList<>(Objects.requireNonNull(navigationContributions, "navigationContributions"));
            return this;
        }

        public Builder documents(List<DocumentContribution> documents) {
            this.documents = new ArrayList<>(Objects.requireNonNull(documents, "documents"));
            return this;
        }

        public Builder publicServices(List<PublicServiceContribution<?>> publicServices) {
            this.publicServices = new ArrayList<>(Objects.requireNonNull(publicServices, "publicServices"));
            return this;
        }

        public Builder events(List<EventContribution> events) {
            this.events = new ArrayList<>(Objects.requireNonNull(events, "events"));
            return this;
        }

        public Builder settings(List<SettingsContribution> settings) {
            this.settings = new ArrayList<>(Objects.requireNonNull(settings, "settings"));
            return this;
        }

        public CapabilityDescriptor build() {
            Objects.requireNonNull(id, "id");
            requireNonBlank(name, "name");
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(description, "description");

            requireUniqueDependencyTargets(id, dependencies);
            requireUniqueKeys(permissions, PermissionDescriptor::permissionId, "permission id");
            requireUniqueKeys(commands, CommandDescriptor::commandId, "command id");
            requireUniqueKeys(views, ViewDescriptor::viewId, "view id");
            requireUniqueKeys(navigationContributions, NavigationContribution::navigationId, "navigation id");
            requireUniqueKeys(documents, DocumentContribution::documentTypeId, "document type id");
            requireUniqueKeys(publicServices, PublicServiceContribution::serviceType, "public service type");
            requireUniqueKeys(events, EventContribution::eventTypeId, "event type id");
            requireUniqueKeys(settings, SettingsContribution::settingKey, "settings key");

            return new CapabilityDescriptor(this);
        }

        private static void requireNonBlank(String value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
        }

        private static void requireUniqueDependencyTargets(CapabilityId ownerId, List<DependencyDescriptor> deps) {
            Set<CapabilityId> seen = new HashSet<>();
            for (DependencyDescriptor dependency : deps) {
                if (!seen.add(dependency.dependencyId())) {
                    throw DependencyValidationException.duplicateDependency(ownerId, dependency.dependencyId());
                }
            }
        }

        private static <T, K> void requireUniqueKeys(List<T> items, Function<T, K> keyExtractor, String category) {
            Set<K> seen = new HashSet<>();
            for (T item : items) {
                K key = keyExtractor.apply(item);
                if (!seen.add(key)) {
                    throw new DuplicateContributionException(
                            "Duplicate " + category + " '" + key + "' in capability descriptor");
                }
            }
        }
    }
}
