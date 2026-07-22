package com.tmp.capability.api;

import java.util.Objects;

/**
 * Immutable, domain-independent metadata for a single node contributed to the navigation
 * tree that the platform's UI builds automatically from active Capabilities.
 *
 * <p>Carries only routing metadata (which {@link ViewDescriptor} it points to and a
 * display order); it contains no rendering logic and does not itself decide which
 * navigation nodes a given user may see.
 */
public final class NavigationContribution {

    private final String navigationId;
    private final String displayName;
    private final String viewId;
    private final int order;

    private NavigationContribution(String navigationId, String displayName, String viewId, int order) {
        this.navigationId = navigationId;
        this.displayName = displayName;
        this.viewId = viewId;
        this.order = order;
    }

    public static NavigationContribution of(String navigationId, String displayName, String viewId, int order) {
        requireNonBlank(navigationId, "navigationId");
        requireNonBlank(displayName, "displayName");
        requireNonBlank(viewId, "viewId");
        return new NavigationContribution(navigationId, displayName, viewId, order);
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    public String navigationId() {
        return navigationId;
    }

    public String displayName() {
        return displayName;
    }

    public String viewId() {
        return viewId;
    }

    public int order() {
        return order;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NavigationContribution that)) {
            return false;
        }
        return navigationId.equals(that.navigationId);
    }

    @Override
    public int hashCode() {
        return navigationId.hashCode();
    }

    @Override
    public String toString() {
        return navigationId;
    }
}
