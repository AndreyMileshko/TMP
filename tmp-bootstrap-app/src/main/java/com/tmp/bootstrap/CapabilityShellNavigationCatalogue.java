package com.tmp.bootstrap;

import com.tmp.capability.api.CapabilityEngine;
import com.tmp.capability.api.CommandDescriptor;
import com.tmp.capability.api.NavigationContribution;
import com.tmp.ui.shell.navigation.ShellNavEntry;
import com.tmp.ui.shell.navigation.ShellNavigationCatalogue;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Bridges Capability Engine navigation/command metadata into the UI-local catalogue.
 */
public final class CapabilityShellNavigationCatalogue implements ShellNavigationCatalogue {

    private final CapabilityEngine capabilityEngine;

    public CapabilityShellNavigationCatalogue(CapabilityEngine capabilityEngine) {
        this.capabilityEngine = Objects.requireNonNull(capabilityEngine, "capabilityEngine");
    }

    @Override
    public List<ShellNavEntry> entries() {
        List<CommandDescriptor> commands = capabilityEngine.activeCommands();
        return capabilityEngine.activeNavigation().stream()
                .map(nav -> toEntry(nav, commands))
                .toList();
    }

    private static ShellNavEntry toEntry(NavigationContribution nav, List<CommandDescriptor> commands) {
        Optional<CommandDescriptor> command = commands.stream()
                .filter(c -> c.commandId().equals(nav.navigationId()))
                .findFirst();
        List<String> required = command.map(CommandDescriptor::requiredPermissionIds).orElse(List.of());
        return ShellNavEntry.of(
                nav.navigationId(),
                nav.displayName(),
                nav.viewId(),
                nav.order(),
                required);
    }
}
