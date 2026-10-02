package com.tmp.ui.shell.screen.production;

import com.tmp.security.api.AccessDeniedException;
import java.util.Locale;
import java.util.Objects;

/**
 * Maps throwables from Production UI operations to safe Russian user messages.
 *
 * <p>Uses only public {@link AccessDeniedException} and message/class-name heuristics — no
 * Production domain/persistence imports.
 */
public final class ProductionUiErrorMapper {

    public static final String ACCESS_DENIED = "Недостаточно прав для производственной операции.";
    public static final String NOT_APPLICABLE =
            "Операция недоступна для текущего состояния производства.";
    public static final String CONCURRENT_STALE =
            "Данные изменились другим пользователем. Экран обновлён.";
    public static final String QUANTITY_MODE_CONFLICT =
            "Режим работы заказа изменён другим пользователем. Данные обновлены.";
    public static final String QUANTITY_MODE_SAVE_FAILED =
            "Не удалось изменить режим работы заказа.";
    public static final String ACCEPT_CONFLICT =
            "Заказ уже принят в производство. Данные обновлены.";
    public static final String ACCEPT_FAILED = "Не удалось принять заказ в производство.";
    public static final String VALIDATION = "Проверьте заполненные данные.";
    public static final String TECHNICAL_FAILURE =
            "Не удалось выполнить производственную операцию. Повторите попытку.";
    public static final String LOAD_FAILED = "Обновление производственных данных не выполнено.";
    public static final String CARD_LOAD_FAILED =
            "Не удалось загрузить данные заказа. Повторите попытку.";
    public static final String ORDER_NOT_FOUND = "Заказ не найден.";
    public static final String TREE_LOAD_FAILED = "Не удалось загрузить производство";

    private ProductionUiErrorMapper() {}

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

            if (simple.contains("OrderQuantityModeOptimisticLock")
                    || (simple.contains("OptimisticLock")
                            && lower.contains("quantity mode"))) {
                return QUANTITY_MODE_CONFLICT;
            }
            if (simple.contains("ProductionLaunchConflict")
                    || simple.contains("AlreadyLaunched")) {
                return ACCEPT_CONFLICT;
            }
            if (simple.contains("OptimisticLock")
                    || simple.contains("Concurrent")
                    || lower.contains("optimistic")
                    || lower.contains("concurrent")
                    || lower.contains("stale")
                    || lower.contains("version mismatch")
                    || lower.contains("expected version")) {
                return CONCURRENT_STALE;
            }
            if (simple.contains("NotAllowed")
                    || simple.contains("NotEligible")
                    || simple.contains("AlreadyExists")
                    || lower.contains("not allowed")
                    || lower.contains("not eligible")
                    || lower.contains("not applicable")) {
                return NOT_APPLICABLE;
            }
            if (simple.contains("IllegalArgument")
                    || lower.contains("must not")
                    || lower.contains("required")
                    || lower.contains("must be")) {
                if ("order not found".equals(lower)) {
                    return ORDER_NOT_FOUND;
                }
                if (containsCyrillic(message)) {
                    return message;
                }
                return VALIDATION;
            }
            if (isOrderNotFoundMessage(simple, lower)) {
                return ORDER_NOT_FOUND;
            }
            current = current.getCause();
        }
        return TECHNICAL_FAILURE;
    }

    public static boolean isQuantityModeConflict(Throwable error) {
        return QUANTITY_MODE_CONFLICT.equals(text(error));
    }

    public static boolean isAcceptConflict(Throwable error) {
        return ACCEPT_CONFLICT.equals(text(error));
    }

    public static boolean isConcurrentOrStale(Throwable error) {
        String mapped = text(error);
        return CONCURRENT_STALE.equals(mapped)
                || QUANTITY_MODE_CONFLICT.equals(mapped)
                || ACCEPT_CONFLICT.equals(mapped);
    }

    private static boolean isOrderNotFoundMessage(String simpleName, String lowerMessage) {
        if ("order not found".equals(lowerMessage)
                || lowerMessage.contains("заказ не найден")) {
            return true;
        }
        if (!(simpleName.contains("NoSuchElement")
                || lowerMessage.contains("not found")
                || lowerMessage.contains("не найден"))) {
            return false;
        }
        return lowerMessage.contains("order") || lowerMessage.contains("заказ");
    }

    private static boolean containsCyrillic(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '\u0400' && c <= '\u04FF') {
                return true;
            }
        }
        return false;
    }
}
