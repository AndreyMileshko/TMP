package com.tmp.ui.shell.screen.audit;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;

/**
 * Presentation-only formatting for Security audit screen. No domain semantics changes.
 */
final class SecurityAuditPresentation {

    private static final DateTimeFormatter OCCURRED_AT_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private SecurityAuditPresentation() {
    }

    static String formatOccurredAt(Instant instant) {
        return formatOccurredAt(instant, ZoneId.systemDefault());
    }

    static String formatOccurredAt(Instant instant, ZoneId zoneId) {
        if (instant == null) {
            return "";
        }
        return OCCURRED_AT_FORMAT.withZone(zoneId).format(instant);
    }

    static String formatOperation(String operation) {
        if (operation == null || operation.isBlank()) {
            return "";
        }
        return switch (operation) {
            case "LOGIN_SUCCESS" -> "Успешный вход";
            case "LOGIN_FAILURE" -> "Неудачный вход";
            case "LOGOUT" -> "Выход";
            case "USER_CREATED" -> "Создание пользователя";
            case "USER_UPDATED" -> "Изменение пользователя";
            case "USER_DELETED" -> "Удаление пользователя";
            case "PASSWORD_CHANGED" -> "Смена пароля";
            case "PASSWORD_RESET" -> "Сброс пароля";
            case "ACTIVATION_CODE_ISSUED" -> "Выдача кода активации";
            case "PASSWORD_INITIALIZED" -> "Инициализация пароля";
            case "ROLE_CREATED" -> "Создание роли";
            case "ROLE_UPDATED" -> "Изменение роли";
            case "ROLE_DELETED" -> "Удаление роли";
            case "ROLE_ASSIGNED" -> "Назначение роли";
            case "ROLE_REVOKED" -> "Отзыв роли";
            case "ROLE_PERMISSIONS_CHANGED" -> "Изменение прав роли";
            case "PERMISSION_GRANTED" -> "Выдача разрешения";
            case "PERMISSION_REVOKED" -> "Отзыв разрешения";
            case "PERMISSION_OVERRIDE_REMOVED" -> "Удаление индивидуального разрешения";
            case "PERMISSION_DEFINITION_REGISTERED" -> "Регистрация разрешения";
            default -> operation;
        };
    }

    static String formatTarget(String targetType, String targetIdentifier) {
        if (targetType == null || targetType.isBlank()) {
            return targetIdentifier == null ? "" : targetIdentifier;
        }
        String typeLabel = switch (targetType) {
            case "USER" -> "Пользователь";
            case "ROLE" -> "Роль";
            case "USER_ROLE" -> "Роль пользователя";
            case "PERMISSION_OVERRIDE" -> "Индивидуальное разрешение";
            default -> targetType;
        };
        if (targetIdentifier == null || targetIdentifier.isBlank()) {
            return typeLabel;
        }
        return typeLabel + " · " + targetIdentifier;
    }

    static String formatResultText(String result) {
        if (result == null) {
            return "";
        }
        return switch (result) {
            case "SUCCESS" -> "Успешно";
            case "FAILURE" -> "Ошибка";
            default -> result;
        };
    }

    static String badgeStyleClass(String result) {
        if (result == null) {
            return "tmp-badge-neutral";
        }
        return switch (result) {
            case "SUCCESS" -> "tmp-badge-success";
            case "FAILURE" -> "tmp-badge-danger";
            default -> "tmp-badge-neutral";
        };
    }

    static Label createResultBadge(String result) {
        Label badge = new Label(formatResultText(result));
        badge.getStyleClass().add("tmp-badge");
        badge.getStyleClass().add(badgeStyleClass(result));
        return badge;
    }

    static StackPane centeredResultBadge(String result) {
        StackPane container = new StackPane(createResultBadge(result));
        container.setAlignment(Pos.CENTER_LEFT);
        return container;
    }

    static Tooltip fullTextTooltip(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Tooltip tooltip = new Tooltip(text);
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(480);
        return tooltip;
    }
}
