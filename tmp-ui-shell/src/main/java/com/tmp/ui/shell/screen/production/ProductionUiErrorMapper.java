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
    public static final String CANCEL_CONFLICT =
            "Производство заказа уже отменено или состояние изменилось. Данные обновлены.";
    public static final String CANCEL_FAILED = "Не удалось отменить производство заказа.";
    public static final String CANCEL_SUCCESS_PREFIX = "Производство заказа №";
    public static final String CANCEL_SUCCESS_SUFFIX = " отменено.";
    public static final String HISTORY_EMPTY = "Операций по производству пока не было";
    public static final String HISTORY_LOAD_FAILED = "Не удалось загрузить историю производства.";
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
    public static final String MATERIALS_CHECK_FAILED =
            "Не удалось проверить наличие материалов.";
    public static final String MATERIALS_UNRESOLVED =
            "Материал не найден в справочнике склада.";
    public static final String NO_PRODUCTION_WAREHOUSE =
            "Не назначен производственный склад.";
    public static final String RELEASE_MATERIALS_NOT_READY =
            "Недостаточно материалов для выпуска.";
    public static final String RELEASE_MODE_CHANGED =
            "Режим работы заказа изменён.\nДанные обновлены.";
    public static final String RELEASE_QUANTITY_CHANGED =
            "Количество к выпуску изменилось.\nДанные обновлены.";
    public static final String RELEASE_QUANTITY_CONFLICT =
            "Количество к выпуску изменилось — другой пользователь уже выпустил часть изделий.\n"
                    + "Данные обновлены.";
    public static final String RELEASE_CANCELLED =
            "Производство заказа отменено.\nВыпуск невозможен.";
    public static final String RELEASE_STOCK_CHANGED =
            "Недостаточно материала на производственном складе.\n\nВыпуск не выполнен.";
    public static final String RELEASE_INVALID_ALLOCATION =
            "Проверьте фактический расход и распределение по ячейкам.";
    public static final String RELEASE_PARTIAL_SUCCESS = "Выпуск завершён частично.";

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
            if (simple.contains("ProductionCancellationAlreadyExists")
                    || (lower.contains("cancellation")
                            && (lower.contains("already")
                                    || lower.contains("only when order production view is"
                                            + " in_production")))) {
                return CANCEL_CONFLICT;
            }
            if (isReleaseCancelled(simple, lower)) {
                return RELEASE_CANCELLED;
            }
            if (isReleaseQuantityConflict(simple, lower)) {
                return RELEASE_QUANTITY_CONFLICT;
            }
            if (isReleaseStockShortage(simple, lower)) {
                return formatReleaseStockShortage(message);
            }
            if (isReleaseInvalidAllocation(simple, lower)) {
                return RELEASE_INVALID_ALLOCATION;
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

    public static boolean isCancelConflict(Throwable error) {
        return CANCEL_CONFLICT.equals(text(error));
    }

    public static String cancelSuccess(String orderNumber) {
        String number = orderNumber == null || orderNumber.isBlank() ? "" : orderNumber.trim();
        if (number.isEmpty()) {
            return "Производство заказа отменено.";
        }
        return CANCEL_SUCCESS_PREFIX + number + CANCEL_SUCCESS_SUFFIX;
    }

    public static boolean isReleaseQuantityConflict(Throwable error) {
        return RELEASE_QUANTITY_CONFLICT.equals(text(error));
    }

    public static boolean isReleaseCancelled(Throwable error) {
        return RELEASE_CANCELLED.equals(text(error));
    }

    public static boolean isReleaseStockShortage(Throwable error) {
        String mapped = text(error);
        return RELEASE_STOCK_CHANGED.equals(mapped) || mapped.contains("Недостаточно материала");
    }

    public static boolean isConcurrentOrStale(Throwable error) {
        String mapped = text(error);
        return CONCURRENT_STALE.equals(mapped)
                || QUANTITY_MODE_CONFLICT.equals(mapped)
                || QUANTITY_MODE_CHANGED_FOR_REQUEST.equals(mapped)
                || ACCEPT_CONFLICT.equals(mapped)
                || CANCEL_CONFLICT.equals(mapped)
                || MATERIAL_DRAFT_CONFLICT.equals(mapped)
                || MATERIAL_COVERAGE_CHANGED.equals(mapped)
                || RELEASE_QUANTITY_CONFLICT.equals(mapped)
                || RELEASE_CANCELLED.equals(mapped);
    }

    private static boolean isReleaseCancelled(String simple, String lower) {
        return lower.contains("release is allowed only when order production view is in_production")
                || (lower.contains("release rejected for item status")
                        && lower.contains("cancelled"));
    }

    private static boolean isReleaseQuantityConflict(String simple, String lower) {
        return lower.contains("exceeds active production quantity")
                || lower.contains("release quantity exceeds")
                || (simple.contains("ReleaseProducts")
                        && lower.contains("active production quantity"));
    }

    private static boolean isReleaseStockShortage(String simple, String lower) {
        return lower.contains("insufficient production warehouse stock")
                || (lower.contains("insufficient")
                        && lower.contains("stock")
                        && (lower.contains("production") || lower.contains("material")));
    }

    private static boolean isReleaseInvalidAllocation(String simple, String lower) {
        return lower.contains("allocation total must equal")
                || lower.contains("duplicate storage cell")
                || lower.contains("storage cell not found")
                || lower.contains("zero actual requires empty allocations")
                || lower.contains("missing confirmed actual usage")
                || lower.contains("extra material not in system-calculated plan");
    }

    private static String formatReleaseStockShortage(String message) {
        // Keep UUID-free human message; detailed code/qty may be appended by caller after readiness
        // refresh.
        if (message == null || message.isBlank()) {
            return RELEASE_STOCK_CHANGED;
        }
        String available = extractAfter(message, "available=");
        String required = extractAfter(message, "required=");
        if (available != null && required != null) {
            return "Недостаточно материала на производственном складе.\n\nДоступно: "
                    + available
                    + "\nТребуется: "
                    + required
                    + ".\n\nВыпуск не выполнен.";
        }
        return RELEASE_STOCK_CHANGED;
    }

    private static String extractAfter(String message, String marker) {
        int index = message.indexOf(marker);
        if (index < 0) {
            return null;
        }
        int start = index + marker.length();
        int end = start;
        while (end < message.length()) {
            char c = message.charAt(end);
            if (c == ',' || c == ' ' || c == ')' || c == ';' || c == '\n') {
                break;
            }
            end++;
        }
        if (end <= start) {
            return null;
        }
        return message.substring(start, end);
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
