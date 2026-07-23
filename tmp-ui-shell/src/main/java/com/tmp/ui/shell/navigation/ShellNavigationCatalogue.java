package com.tmp.ui.shell.navigation;

import java.util.List;

/**
 * UI-local navigation catalogue. Bootstrap bridges Capability Engine metadata into this type
 * so {@code com.tmp.ui..} never depends on {@code com.tmp.capability..}.
 */
public interface ShellNavigationCatalogue {

    List<ShellNavEntry> entries();
}
