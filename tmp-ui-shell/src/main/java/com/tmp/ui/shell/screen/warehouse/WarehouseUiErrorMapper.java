package com.tmp.ui.shell.screen.warehouse;

import com.tmp.security.api.AccessDeniedException;
import java.util.Locale;
import java.util.Objects;

/**
 * Maps throwables from Warehouse UI operations to safe Russian user messages.
 *
 * <p>Uses only public {@link AccessDeniedException} and message heuristics — no Warehouse
 * application/persistence imports.
 */
public final class WarehouseUiErrorMapper {

    public static final String ACCESS_DENIED = "Недостаточно прав для складской операции.";
    public static final String VALIDATION = "Проверьте заполненные данные.";
    public static final String NOT_FOUND = "Складские данные не найдены.";
    public static final String INSUFFICIENT_STOCK = "Недостаточно остатка для операции.";
    public static final String TECHNICAL_FAILURE =
            "Не удалось выполнить складскую операцию. Повторите попытку.";
    public static final String LOAD_FAILED = "Обновление складских данных не выполнено.";

    private WarehouseUiErrorMapper() {}

    public static String text(Throwable error) {
        Objects.requireNonNull(error, "error");
        Throwable current = error;
        while (current != null) {
            if (current instanceof AccessDeniedException) {
                return ACCESS_DENIED;
            }
            String message = current.getMessage() == null ? "" : current.getMessage();
            String lower = message.toLowerCase(Locale.ROOT);
            String simple = current.getClass().getSimpleName();
            if (simple.contains("IllegalArgument") || lower.contains("must not") || lower.contains("required")) {
                return VALIDATION;
            }
            if (simple.contains("NoSuchElement") || lower.contains("not found")) {
                return NOT_FOUND;
            }
            if (lower.contains("insufficient") || lower.contains("недостат")) {
                return INSUFFICIENT_STOCK;
            }
            current = current.getCause();
        }
        return TECHNICAL_FAILURE;
    }
}
