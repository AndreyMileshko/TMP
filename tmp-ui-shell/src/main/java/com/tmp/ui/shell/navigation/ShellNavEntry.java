package com.tmp.ui.shell.navigation;

import java.util.List;
import java.util.Objects;

/**
 * One navigation entry for the main window, including required permission ids for gating.
 */
public record ShellNavEntry(
        String navigationId,
        String displayName,
        String viewId,
        int order,
        List<String> requiredPermissionIds) {

    public ShellNavEntry {
        Objects.requireNonNull(navigationId, "navigationId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(viewId, "viewId");
        requiredPermissionIds = List.copyOf(Objects.requireNonNull(requiredPermissionIds, "requiredPermissionIds"));
    }

    public static ShellNavEntry of(
            String navigationId,
            String displayName,
            String viewId,
            int order,
            List<String> requiredPermissionIds) {
        return new ShellNavEntry(navigationId, displayName, viewId, order, requiredPermissionIds);
    }
}
