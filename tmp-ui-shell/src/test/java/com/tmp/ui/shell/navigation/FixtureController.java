package com.tmp.ui.shell.navigation;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Test FXML controller implementing {@link ViewModelAware}.
 */
public final class FixtureController implements ViewModelAware<FixtureViewModel> {

    @FXML
    private Label label;

    private FixtureViewModel viewModel;

    @Override
    public void setViewModel(FixtureViewModel viewModel) {
        this.viewModel = viewModel;
        if (label != null && viewModel != null) {
            label.setText(viewModel.label());
        }
    }

    public FixtureViewModel viewModel() {
        return viewModel;
    }

    public Label label() {
        return label;
    }
}
