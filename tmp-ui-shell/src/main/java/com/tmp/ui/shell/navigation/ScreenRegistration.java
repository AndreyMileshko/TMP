package com.tmp.ui.shell.navigation;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Registration of a navigable screen: FXML resource plus a ViewModel supplier.
 */
public record ScreenRegistration(
        String screenId, String fxmlClasspathResource, Supplier<Object> viewModelSupplier) {

    public ScreenRegistration {
        Objects.requireNonNull(screenId, "screenId");
        if (screenId.isBlank()) {
            throw new IllegalArgumentException("screenId must not be blank");
        }
        Objects.requireNonNull(fxmlClasspathResource, "fxmlClasspathResource");
        if (fxmlClasspathResource.isBlank()) {
            throw new IllegalArgumentException("fxmlClasspathResource must not be blank");
        }
        Objects.requireNonNull(viewModelSupplier, "viewModelSupplier");
    }
}
