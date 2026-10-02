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

    public static final String ACCESS_DENIED = "Недостаточно прав для этого действия";
    public static final String NOT_APPLICABLE =
            "Операция недоступна для текущего состояния производства.";
    public static final String CONCURRENT_STALE =
            "Данные изменились другим пользователем. Экран обновлён.";
    public static final String QUANTITY_MODE_CONFLICT =
            "Режим работы заказа изменён другим пользователем. Данные обновлены.";
    public static final String QUANTITY_MODE_CHANGED_FOR_REQUEST =
            "Режим работы заказа изменён. Данные обновлены.";
    public static final String QUANTITY_MODE_SAVE_FAILED =
            "Не удалось изменить режим работы заказа.";
    public static final String ACCEPT_CONFLICT =
            "Заказ уже принят в производство. Данные обновлены.";
    public static final String ACCEPT_FAILED = "Не удалось принять заказ в производство.";
    public static final String VALIDATION = "Проверьте заполненные данные.";
    public static final String TECHNICAL_FAILURE =
            "Не удалось выполнить операцию. Повторите попытку.";
    public static final String LOAD_FAILED = "Обновление производственных данных не выполнено.";
    public static final String CARD_LOAD_FAILED =
            "Не удалось загрузить данные заказа. Повторите попытку.";
    public static final String ORDER_NOT_FOUND = "Заказ не найден.";
    public static final String TREE_LOAD_FAILED = "Не удалось загрузить производство";
    public static final String MATERIAL_COVERAGE_CHANGED =
            "Количество материалов для позиции изменилось. Данные обновлены.";
    public static final String MATERIAL_COVERAGE_CONFLICT =
            "Количество для одной или нескольких позиций уже изменилось из-за другого запроса"
                    + " материалов.\n\nЧерновик не отправлен.";
    public static final String MATERIAL_SHORTAGE =
            "Недостаточно материалов для формирования складского перемещения.\n\nЧерновик сохранён.";
    public static final String MATERIAL_DRAFT_CONFLICT =
            "Черновик изменён другим пользователем. Данные обновлены.";
    public static final String MATERIAL_SUBMIT_SUCCESS =
            "Запрос материалов отправлен на склад.";
    public static final String MATERIAL_SUBMIT_SUCCESS_HINT =
            "Запрос материалов отправлен на склад.\nСклад сформирует необходимые перемещения.";
    public static final String MATERIAL_PREPARE_FAILED =
            "Не удалось подготовить запрос материалов. Повторите попытку.";

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

            if (simple.contains("MaterialRequirementCoverageConflict")
                    || lower.contains("product coverage conflict")
                    || lower.contains("materialrequirementcoverageconflict")
                    || lower.contains("coverage conflict")) {
                return MATERIAL_COVERAGE_CONFLICT;
            }
            if (simple.contains("MaterialRequirementShortage")
                    || lower.contains("materialrequirementshortage")
                    || lower.contains("нет ни одного склада-источника")
                    || (lower.contains("shortage") && lower.contains("material"))) {
                return MATERIAL_SHORTAGE;
            }
            if (simple.contains("MaterialRequirementOptimisticLock")) {
                return MATERIAL_DRAFT_CONFLICT;
            }
            if (isModeChangedForMaterialRequest(simple, lower)) {
                return QUANTITY_MODE_CHANGED_FOR_REQUEST;
            }
            if (isCoverageChangedForMaterialRequest(simple, lower)) {
                return MATERIAL_COVERAGE_CHANGED;
            }
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
        String mapped = text(error);
        return QUANTITY_MODE_CONFLICT.equals(mapped)
                || QUANTITY_MODE_CHANGED_FOR_REQUEST.equals(mapped);
    }

    public static boolean isMaterialModeChanged(Throwable error) {
        return QUANTITY_MODE_CHANGED_FOR_REQUEST.equals(text(error));
    }

    public static boolean isMaterialCoverageChanged(Throwable error) {
        return MATERIAL_COVERAGE_CHANGED.equals(text(error));
    }

    public static boolean isMaterialCoverageConflict(Throwable error) {
        return MATERIAL_COVERAGE_CONFLICT.equals(text(error));
    }

    public static boolean isMaterialShortage(Throwable error) {
        return MATERIAL_SHORTAGE.equals(text(error));
    }

    public static boolean isMaterialDraftConflict(Throwable error) {
        return MATERIAL_DRAFT_CONFLICT.equals(text(error));
    }

    public static boolean isAcceptConflict(Throwable error) {
        return ACCEPT_CONFLICT.equals(text(error));
    }

    public static boolean isConcurrentOrStale(Throwable error) {
        String mapped = text(error);
        return CONCURRENT_STALE.equals(mapped)
                || QUANTITY_MODE_CONFLICT.equals(mapped)
                || QUANTITY_MODE_CHANGED_FOR_REQUEST.equals(mapped)
                || ACCEPT_CONFLICT.equals(mapped)
                || MATERIAL_DRAFT_CONFLICT.equals(mapped)
                || MATERIAL_COVERAGE_CHANGED.equals(mapped);
    }

    private static boolean isModeChangedForMaterialRequest(String simple, String lower) {
        return lower.contains("standard mode must not")
                || lower.contains("flexible mode requires")
                || lower.contains("must not supply requestedproductquantity")
                || lower.contains("requires requestedproductquantity");
    }

    private static boolean isCoverageChangedForMaterialRequest(String simple, String lower) {
        return lower.contains("no requestable")
                || lower.contains("exceeds requestable")
                || (lower.contains("requestable product quantity")
                        && (simple.contains("Selection") || lower.contains("selection")));
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
