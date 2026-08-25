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
    public static final String MATERIAL_UNRESOLVED = "Материал не сопоставлен.";
    public static final String MATERIAL_AMBIGUOUS = "Неоднозначное сопоставление материала.";
    public static final String INSUFFICIENT_STOCK = "Недостаточно остатка материалов.";
    public static final String TRANSFER_NOT_SENT =
            "Материалы ещё не отправлены складом. Получение недоступно.";
    public static final String STALE_TEMPLATE =
            "Шаблон перемещения устарел. Обновите данные и повторите.";
    public static final String INVALID_RELEASE_QTY = "Некорректное количество выпуска.";
    public static final String CONCURRENT_STALE =
            "Данные изменились другим пользователем. Экран обновлён.";
    public static final String VALIDATION = "Проверьте заполненные данные.";
    public static final String TECHNICAL_FAILURE =
            "Не удалось выполнить производственную операцию. Повторите попытку.";
    public static final String LOAD_FAILED = "Обновление производственных данных не выполнено.";
    public static final String ORDER_NOT_FOUND = "Заказ не найден.";

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

            if (simple.contains("OptimisticLock")
                    || simple.contains("Concurrent")
                    || lower.contains("optimistic")
                    || lower.contains("concurrent")
                    || lower.contains("stale")
                    || lower.contains("version mismatch")
                    || lower.contains("expected version")) {
                return CONCURRENT_STALE;
            }
            if (simple.contains("NotEditable")
                    || (lower.contains("template") && lower.contains("stale"))
                    || (lower.contains("template") && lower.contains("confirmed"))) {
                return STALE_TEMPLATE;
            }
            if (lower.contains("draft")
                    && (lower.contains("receipt")
                            || lower.contains("not ready")
                            || lower.contains("not physically")
                            || lower.contains("не отправл"))) {
                return TRANSFER_NOT_SENT;
            }
            if (simple.contains("MaterialTransferTemplateNotReady")
                    || lower.contains("unresolved")
                    || lower.contains("material_unresolved")
                    || lower.contains("не сопоставл")) {
                return MATERIAL_UNRESOLVED;
            }
            if (lower.contains("ambiguous") || lower.contains("неоднознач")) {
                return MATERIAL_AMBIGUOUS;
            }
            if (lower.contains("insufficient") || lower.contains("недостат")) {
                return INSUFFICIENT_STOCK;
            }
            if (simple.contains("Release")
                    && (lower.contains("quantity")
                            || lower.contains("release amount")
                            || lower.contains("exceeds")
                            || lower.contains("invalid release"))) {
                return INVALID_RELEASE_QTY;
            }
            if (simple.contains("NotAllowed")
                    || simple.contains("NotEligible")
                    || simple.contains("AlreadyLaunched")
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
                if (containsCyrillic(message)) {
                    return message;
                }
                return VALIDATION;
            }
            if (simple.contains("NoSuchElement")
                    || lower.contains("not found")
                    || lower.contains("не найден")) {
                return ORDER_NOT_FOUND;
            }
            current = current.getCause();
        }
        return TECHNICAL_FAILURE;
    }

    public static boolean isConcurrentOrStale(Throwable error) {
        return CONCURRENT_STALE.equals(text(error)) || STALE_TEMPLATE.equals(text(error));
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
