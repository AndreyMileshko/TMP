package com.tmp.capability.api;

import java.util.List;
import java.util.Optional;

/**
 * Stable public facade for Capability Engine orchestration and read-only workplace
 * catalog queries. External modules and the bootstrap layer must depend only on this
 * interface (and other types in {@code com.tmp.capability.api}) for capability
 * orchestration — never on internal collaborators.
 *
 * <p>Workplace catalog methods ({@link #activePermissions()}, {@link #activeCommands()},
 * {@link #activeViews()}, {@link #activeNavigation()}) return metadata for
 * <em>currently active</em> Capabilities only. They never make access decisions for a
 * specific user.
 */
public interface CapabilityEngine {

    void discoverAndRegisterAll();

    void activateAll();

    void deactivate(CapabilityId id);

    void stopAll();

    Optional<CapabilityDescriptor> findById(CapabilityId id);

    List<CapabilityDescriptor> registeredCapabilities();

    CapabilityLifecycleState stateOf(CapabilityId id);

    List<PermissionDescriptor> activePermissions();

    List<CommandDescriptor> activeCommands();

    List<ViewDescriptor> activeViews();

    List<NavigationContribution> activeNavigation();

    CapabilityEngineStatus status();
}
