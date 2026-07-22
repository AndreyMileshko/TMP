package com.tmp.capability.api;

import java.util.Objects;

/**
 * Immutable, domain-independent metadata describing a view that a Capability contributes.
 *
 * <p>Carries only routing metadata: an identifier, a display name, and the id of the
 * {@link NavigationContribution} that leads to this view. It does not reference any FXML
 * file, Controller, or ViewModel class, nor does it instantiate a screen — resolving this
 * metadata into an actual screen is deferred to future UI stages.
 */
public final class ViewDescriptor {

    private final String viewId;
    private final String displayName;
    private final String navigationTargetId;

    private ViewDescriptor(String viewId, String displayName, String navigationTargetId) {
        this.viewId = viewId;
        this.displayName = displayName;
        this.navigationTargetId = navigationTargetId;
    }

    public static ViewDescriptor of(String viewId, String displayName, String navigationTargetId) {
        requireNonBlank(viewId, "viewId");
        requireNonBlank(displayName, "displayName");
        requireNonBlank(navigationTargetId, "navigationTargetId");
        return new ViewDescriptor(viewId, displayName, navigationTargetId);
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    public String viewId() {
        return viewId;
    }

    public String displayName() {
        return displayName;
    }

    public String navigationTargetId() {
        return navigationTargetId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ViewDescriptor that)) {
            return false;
        }
        return viewId.equals(that.viewId);
    }

    @Override
    public int hashCode() {
        return viewId.hashCode();
    }

    @Override
    public String toString() {
        return viewId;
    }
}
