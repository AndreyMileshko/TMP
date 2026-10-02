package com.tmp.ui.shell.screen.production;

import com.tmp.production.api.ProductionApplicationApi.QuantityModeView;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateStatus;
import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import java.util.Objects;

/**
 * Russian presentation labels for Production Query DTOs. No business logic.
 */
public final class ProductionPresentationLabels {

    private ProductionPresentationLabels() {}

    public static String orderStatus(OrderProductionViewStatus status) {
        Objects.requireNonNull(status, "status");
        return switch (status) {
            case NOT_ACCEPTED -> "Не принят";
            case IN_PRODUCTION -> "В производстве";
            case MANUFACTURED -> "Изготовлен";
            case CANCELLED -> "Отменён";
        };
    }

    public static String itemStatus(ItemProductionStateStatus status) {
        if (status == null) {
            return "Не принято";
        }
        return switch (status) {
            case IN_PRODUCTION -> "В производстве";
            case PARTIALLY_RELEASED -> "Частично выпущено";
            case RELEASED -> "Выпущено";
            case CANCELLED -> "Отменено";
        };
    }

    public static String quantityMode(QuantityModeView mode) {
        Objects.requireNonNull(mode, "mode");
        return switch (mode) {
            case STANDARD -> "Стандартный";
            case FLEXIBLE -> "Гибкий";
        };
    }

    public static String quantityModeHint(QuantityModeView mode) {
        Objects.requireNonNull(mode, "mode");
        return switch (mode) {
            case STANDARD ->
                    "Система использует всё доступное количество выбранной позиции.";
            case FLEXIBLE ->
                    "Количество можно указать вручную при запросе материалов и выпуске.";
        };
    }

    public static String materialsSummary(
            com.tmp.production.api.ProductionApplicationApi.MaterialReadinessView readiness) {
        if (readiness == null) {
            return "";
        }
        return switch (readiness.status()) {
            case READY ->
                    "✓ Материалов достаточно для выпуска оставшихся изделий.";
            case NOT_READY ->
                    "Недостаточно материалов для выпуска оставшихся изделий.";
            case NO_PRODUCTION_WAREHOUSE -> "Не назначен производственный склад.";
            case MATERIAL_REFERENCE_UNRESOLVED ->
                    "Материал не найден в справочнике склада.";
            case NOT_APPLICABLE ->
                    switch (readiness.reason()) {
                        case NOT_ACCEPTED ->
                                "Материалы будут доступны после принятия заказа в производство.";
                        case MANUFACTURED -> "Все изделия выпущены.";
                        case CANCELLED -> "Производство заказа отменено.";
                        case NO_RELEASABLE_QUANTITY -> "Нет изделий для выпуска.";
                        default -> "Проверка материалов сейчас недоступна.";
                    };
        };
    }

    public static String materialsDetail(
            com.tmp.production.api.ProductionApplicationApi.MaterialReadinessView readiness) {
        if (readiness == null) {
            return "";
        }
        if (readiness.status()
                == com.tmp.production.api.ProductionApplicationApi.MaterialReadinessStatusView
                        .NOT_READY) {
            int count = readiness.deficientLineCount();
            if (count <= 0) {
                return "";
            }
            return "Не хватает: " + count + " " + positionWord(count) + " материалов";
        }
        return "";
    }

    private static String positionWord(int count) {
        int mod100 = count % 100;
        int mod10 = count % 10;
        if (mod100 >= 11 && mod100 <= 14) {
            return "позиций";
        }
        if (mod10 == 1) {
            return "позиция";
        }
        if (mod10 >= 2 && mod10 <= 4) {
            return "позиции";
        }
        return "позиций";
    }
}
