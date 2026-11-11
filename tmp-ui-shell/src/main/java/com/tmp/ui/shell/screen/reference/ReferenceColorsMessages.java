package com.tmp.ui.shell.screen.reference;

import com.tmp.security.api.AccessDeniedException;

/**
 * Maps Reference Data / Security exceptions to concise Russian UI text (COL-030A/B).
 *
 * <p>Does not depend on {@code com.tmp.reference.domain} — only exception class names and messages
 * from public API calls.
 */
final class ReferenceColorsMessages {

    private ReferenceColorsMessages() {}

    static String mapError(RuntimeException ex) {
        if (ex instanceof AccessDeniedException) {
            return "Недостаточно прав для выполнения операции.";
        }
        String type = ex.getClass().getName();
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        if (type.endsWith("DuplicateCodeException")
                || message.contains("already exists (case-insensitive)")) {
            return "Цвет с таким кодом уже существует (без учёта регистра).";
        }
        if (type.endsWith("InvalidCodeException")) {
            if (message.contains("must not be blank") || message.contains("must not be null")) {
                return "Укажите код цвета.";
            }
            if (message.contains("length must be")) {
                return "Код цвета не длиннее 31 символа.";
            }
            if (message.contains("'/' or '\\'")) {
                return "Код цвета не должен содержать символы «/» или «\\».";
            }
            if (message.contains("at most one '|'")) {
                return "В коде цвета допускается не более одного символа «|».";
            }
            if (message.contains("non-empty")) {
                return "Части кода до и после «|» не должны быть пустыми.";
            }
            return "Некорректный код цвета.";
        }
        if (type.endsWith("OptimisticLockConflictException")
                || message.contains("version conflict")
                || message.contains("Version conflict")) {
            return "Данные изменились. Обновите список и повторите действие.";
        }
        if (message.contains("already archived")) {
            return "Цвет уже в архиве.";
        }
        if (message.contains("already active")) {
            return "Цвет уже активен.";
        }
        if (message.contains("Color not found")) {
            return "Цвет не найден. Обновите список.";
        }
        if (!message.isBlank()) {
            return message;
        }
        return "Не удалось выполнить операцию.";
    }

    static String mapMappingError(RuntimeException ex) {
        if (ex instanceof AccessDeniedException) {
            return "Недостаточно прав для выполнения операции.";
        }
        String type = ex.getClass().getName();
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        if (type.endsWith("DuplicateSourceKeyException")
                || message.contains("Сопоставление для этого текста уже существует")
                || message.contains("already exists")) {
            return "Сопоставление для этого текста уже существует (без учёта регистра и лишних"
                    + " пробелов).";
        }
        if (type.endsWith("InvalidSourceException")) {
            if (message.contains("must not be blank") || message.contains("must not be null")) {
                return "Укажите исходное обозначение СО7.";
            }
            if (message.contains("length must be")) {
                return "Исходное обозначение СО7 не длиннее 64 символов.";
            }
            return "Некорректное исходное обозначение СО7.";
        }
        if (type.endsWith("OptimisticLockConflictException")
                || message.contains("version conflict")
                || message.contains("Version conflict")) {
            return "Данные изменились. Обновите список и повторите действие.";
        }
        if (message.contains("Referenced color is inactive")) {
            return "Выбранный цвет TMP неактивен. Выберите активный цвет.";
        }
        if (message.contains("Color not found")) {
            return "Цвет TMP не найден. Обновите список и повторите.";
        }
        if (message.contains("SO7 mapping not found")) {
            return "Соответствие не найдено. Обновите список.";
        }
        if (!message.isBlank()) {
            return message;
        }
        return "Не удалось выполнить операцию.";
    }
}
