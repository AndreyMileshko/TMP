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
}
