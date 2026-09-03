package com.tmp.ui.shell.navigation;

import java.util.Objects;

/**
 * One session-only Shell content destination. Restore must not push a new history entry.
 */
public final class ShellHistoryEntry {

    private final String screenId;
    private final String requiredPermission;
    private final Runnable restore;

    private ShellHistoryEntry(String screenId, String requiredPermission, Runnable restore) {
        this.screenId = Objects.requireNonNull(screenId, "screenId");
        this.requiredPermission = requiredPermission;
        this.restore = Objects.requireNonNull(restore, "restore");
    }

    public static ShellHistoryEntry of(String screenId, String requiredPermission, Runnable restore) {
        return new ShellHistoryEntry(
                screenId, requiredPermission, restore == null ? () -> {} : restore);
    }

    public String screenId() {
        return screenId;
    }

    public String requiredPermission() {
        return requiredPermission;
    }

    public Runnable restore() {
        return restore;
    }
}
