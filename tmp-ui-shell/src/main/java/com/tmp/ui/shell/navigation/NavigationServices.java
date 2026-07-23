package com.tmp.ui.shell.navigation;

/**
 * Factory for the default {@link NavigationService} implementation (keeps the
 * implementation type package-private).
 */
public final class NavigationServices {

    private NavigationServices() {
    }

    public static NavigationService createDefault() {
        return new DefaultNavigationService();
    }
}
