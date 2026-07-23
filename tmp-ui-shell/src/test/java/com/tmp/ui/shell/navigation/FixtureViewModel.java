package com.tmp.ui.shell.navigation;

/**
 * Test ViewModel injected into {@link FixtureController}.
 */
public final class FixtureViewModel {

    private final String label;

    public FixtureViewModel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
