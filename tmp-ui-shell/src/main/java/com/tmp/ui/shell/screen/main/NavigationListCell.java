package com.tmp.ui.shell.screen.main;

import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

/**
 * Navigation row presentation: icon + label with shell styling.
 */
final class NavigationListCell extends ListCell<NavigationItem> {

    private final HBox row = new HBox(10);
    private final StackPane iconContainer = new StackPane();
    private final SVGPath icon = new SVGPath();
    private final Label label = new Label();

    NavigationListCell() {
        row.getStyleClass().add("main-window-nav-cell");
        iconContainer.getStyleClass().add("main-window-nav-icon-container");
        icon.getStyleClass().add("main-window-nav-icon");
        label.getStyleClass().add("main-window-nav-label");
        icon.setScaleX(0.75);
        icon.setScaleY(0.75);
        iconContainer.getChildren().add(icon);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().addAll(iconContainer, label);
        setGraphic(row);
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    @Override
    protected void updateItem(NavigationItem item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            label.setText(null);
            icon.setContent(null);
            setGraphic(null);
            applySelectedStyle(false);
            return;
        }
        label.setText(item.displayName());
        ShellNavigationIcons.apply(icon, item.navigationId());
        setGraphic(row);
        applySelectedStyle(isSelected());
    }

    @Override
    public void updateSelected(boolean selected) {
        super.updateSelected(selected);
        applySelectedStyle(selected);
    }

    private void applySelectedStyle(boolean selected) {
        row.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("selected"), selected);
    }
}
