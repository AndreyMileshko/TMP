package com.tmp.capability.api;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Objects;

/**
 * Immutable, domain-independent description of a command that a Capability contributes.
 * Pure metadata: no execution logic, no authorization checks, no UI binding.
 */
public final class CommandDescriptor {

    private final String commandId;
    private final String displayName;
    private final List<String> requiredPermissionIds;

    private CommandDescriptor(String commandId, String displayName, List<String> requiredPermissionIds) {
        this.commandId = commandId;
        this.displayName = displayName;
        this.requiredPermissionIds = requiredPermissionIds;
    }

    public static CommandDescriptor of(String commandId, String displayName, List<String> requiredPermissionIds) {
        requireNonBlank(commandId, "commandId");
        requireNonBlank(displayName, "displayName");
        Objects.requireNonNull(requiredPermissionIds, "requiredPermissionIds");
        return new CommandDescriptor(commandId, displayName, List.copyOf(requiredPermissionIds));
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    public String commandId() {
        return commandId;
    }

    public String displayName() {
        return displayName;
    }

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "requiredPermissionIds is an unmodifiable list produced via List.copyOf at "
                    + "construction time; it is safe to return the field directly.")
    public List<String> requiredPermissionIds() {
        return requiredPermissionIds;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CommandDescriptor that)) {
            return false;
        }
        return commandId.equals(that.commandId);
    }

    @Override
    public int hashCode() {
        return commandId.hashCode();
    }

    @Override
    public String toString() {
        return commandId;
    }
}
