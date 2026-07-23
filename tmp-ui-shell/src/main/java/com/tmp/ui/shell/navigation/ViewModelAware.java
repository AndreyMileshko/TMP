package com.tmp.ui.shell.navigation;

/**
 * Marker for FXML controllers that receive a ViewModel after load.
 *
 * @param <T> view-model type
 */
public interface ViewModelAware<T> {

    void setViewModel(T viewModel);
}
