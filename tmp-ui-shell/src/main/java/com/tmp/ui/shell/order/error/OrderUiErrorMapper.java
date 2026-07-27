package com.tmp.ui.shell.order.error;

import com.tmp.security.api.AccessDeniedException;
import java.util.Locale;
import java.util.Objects;

/**
 * Maps throwables from Order Management UI operations to safe Russian user messages.
 *
 * <p>Does not depend on Spring and does not import Order Management application/persistence or
 * Document Engine internals. Classification uses {@link AccessDeniedException}, simple class names
 * and message keywords along the cause chain.
 */
public final class OrderUiErrorMapper {

    public static final String ACCESS_DENIED =
            "Недостаточно прав для выполнения операции.";
    public static final String OPTIMISTIC_LOCK =
            "Данные были изменены другим пользователем. Обновите экран и повторите действие.";
    public static final String VALIDATION = "Проверьте заполненные данные.";
    public static final String NOT_FOUND = "Объект не найден или был удалён.";
    public static final String FORBIDDEN_TRANSITION =
            "Операция недоступна для текущего состояния объекта. Обновите данные.";
    public static final String ALREADY_POSTED = "Документ уже был проведён. Обновите данные.";
    public static final String UNPOST_NOT_SUPPORTED =
            "Отмена проведения для этого документа не поддерживается.";
    public static final String TECHNICAL_FAILURE =
            "Не удалось выполнить операцию. Повторите попытку или обратитесь к администратору.";
    public static final String UNSAVED_CHANGES_BEFORE_POST =
            "Сохраните последние изменения перед проведением документа.";
    public static final String RELOAD_FAILED_AFTER_POST =
            "Документ проведён, но обновить данные на экране не удалось.";
    public static final String LIST_REFRESH_FAILED = "Обновление списка не выполнено.";
    public static final String APPROVED_SPEC_READ_ONLY =
            "Утверждённая спецификация доступна только для просмотра.";

    private OrderUiErrorMapper() {
    }

    public static OrderUiUserMessage map(Throwable error, OrderUiOperation operation) {
        Objects.requireNonNull(error, "error");
        Objects.requireNonNull(operation, "operation");
        OrderUiErrorCategory category = classify(error, operation);
        return OrderUiUserMessage.of(category, messageFor(category));
    }

    public static String text(Throwable error, OrderUiOperation operation) {
        return map(error, operation).text();
    }

    private static OrderUiErrorCategory classify(Throwable error, OrderUiOperation operation) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof AccessDeniedException) {
                return OrderUiErrorCategory.ACCESS_DENIED;
            }
            String simpleName = current.getClass().getSimpleName();
            String message = current.getMessage() == null ? "" : current.getMessage();
            String lower = message.toLowerCase(Locale.ROOT);

            if (containsIgnoreCase(simpleName, "OptimisticLock")
                    || containsIgnoreCase(simpleName, "PayloadOptimisticLock")
                    || lower.contains("payload revision conflict")
                    || lower.contains("optimistic lock")
                    || lower.contains("version conflict")) {
                return OrderUiErrorCategory.OPTIMISTIC_LOCK;
            }
            if (containsIgnoreCase(simpleName, "UnsupportedOperation")
                    || lower.contains("unpost is not supported")
                    || lower.contains("unpost not supported")) {
                return OrderUiErrorCategory.UNPOST_NOT_SUPPORTED;
            }
            if (operation == OrderUiOperation.POST_DOCUMENT
                    && (lower.contains("requires draft status")
                            || lower.contains("already posted")
                            || lower.contains("already processed"))) {
                return OrderUiErrorCategory.ALREADY_POSTED;
            }
            if (containsIgnoreCase(simpleName, "NotFound")
                    || lower.contains("not found")
                    || lower.contains("не найден")) {
                return OrderUiErrorCategory.NOT_FOUND;
            }
            if (containsIgnoreCase(simpleName, "InvalidOrderState")
                    || containsIgnoreCase(simpleName, "ApprovalRejected")
                    || containsIgnoreCase(simpleName, "NonDraftPayload")
                    || lower.contains("cannot re-approve")
                    || lower.contains("already has draft")
                    || lower.contains("immutable")
                    || lower.contains("not editable")
                    || lower.contains("forbidden")
                    || lower.contains("недопустимо")
                    || lower.contains("недоступн")) {
                return OrderUiErrorCategory.FORBIDDEN_TRANSITION;
            }
            if (current instanceof IllegalArgumentException
                    || containsIgnoreCase(simpleName, "Validation")
                    || lower.contains("must be")
                    || lower.contains("must not")
                    || lower.contains("invalid")) {
                return OrderUiErrorCategory.VALIDATION;
            }
            current = current.getCause();
        }
        return OrderUiErrorCategory.TECHNICAL_FAILURE;
    }

    private static String messageFor(OrderUiErrorCategory category) {
        return switch (category) {
            case ACCESS_DENIED -> ACCESS_DENIED;
            case OPTIMISTIC_LOCK -> OPTIMISTIC_LOCK;
            case VALIDATION -> VALIDATION;
            case NOT_FOUND -> NOT_FOUND;
            case FORBIDDEN_TRANSITION -> FORBIDDEN_TRANSITION;
            case ALREADY_POSTED -> ALREADY_POSTED;
            case UNPOST_NOT_SUPPORTED -> UNPOST_NOT_SUPPORTED;
            case TECHNICAL_FAILURE -> TECHNICAL_FAILURE;
        };
    }

    private static boolean containsIgnoreCase(String value, String fragment) {
        return value.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT));
    }
}
