package com.tmp.ui.shell.screen.useradmin;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/**
 * Maps domain user status values to TMP UI Standard badge presentation.
 */
final class UserStatusPresentation {

    private UserStatusPresentation() {
    }

    static Label createStatusBadge(String status) {
        Label badge = new Label(displayText(status));
        badge.getStyleClass().add("tmp-badge");
        badge.getStyleClass().add(badgeStyleClass(status));
        return badge;
    }

    static String displayText(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "ACTIVE" -> "Активен";
            case "DELETED" -> "Удалён";
            default -> status;
        };
    }

    static String badgeStyleClass(String status) {
        if (status == null) {
            return "tmp-badge-neutral";
        }
        return switch (status) {
            case "ACTIVE" -> "tmp-badge-success";
            case "DELETED" -> "tmp-badge-danger";
            default -> "tmp-badge-neutral";
        };
    }

    static StackPane centeredBadgeCell(String status) {
        StackPane container = new StackPane(createStatusBadge(status));
        container.setAlignment(Pos.CENTER_LEFT);
        return container;
    }
}
