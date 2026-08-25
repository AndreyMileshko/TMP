package com.tmp.ui.shell.screen.production;

import com.tmp.production.api.ProductionQueryApi.ItemProductionStateStatus;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateView;
import com.tmp.production.api.ProductionQueryApi.MaterialAvailabilityLineStatus;
import com.tmp.production.api.ProductionQueryApi.MaterialAvailabilityLineView;
import com.tmp.production.api.ProductionQueryApi.MaterialPlanningSourceView;
import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import com.tmp.production.api.ProductionQueryApi.ProductionHistoryType;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

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

    public static String orderStatusDetail(OrderProductionViewStatus status) {
        Objects.requireNonNull(status, "status");
        return switch (status) {
            case NOT_ACCEPTED -> "Доступен для производства";
            case IN_PRODUCTION -> "В производстве";
            case MANUFACTURED -> "Изготовлен";
            case CANCELLED -> "Отменён";
        };
    }

    public static String itemStatus(ItemProductionStateStatus status) {
        if (status == null) {
            return "—";
        }
        return switch (status) {
            case IN_PRODUCTION -> "В производстве";
            case PARTIALLY_RELEASED -> "Частично изготовлено";
            case RELEASED -> "Изготовлено";
            case CANCELLED -> "Отменено";
        };
    }

    public static String historyType(ProductionHistoryType type) {
        Objects.requireNonNull(type, "type");
        return switch (type) {
            case ORDER_ACCEPTED -> "Заказ принят в производство";
            case MATERIALS_CHECKED -> "Проверка материалов";
            case MATERIAL_TRANSFER_CREATED -> "Создано перемещение материалов";
            case MATERIAL_RECEIPT_CONFIRMED -> "Подтверждено получение материалов";
            case PRODUCTS_RELEASED -> "Выпуск изделий";
            case PLAN_FACT_DEVIATION -> "Отклонение план/факт";
            case PRODUCTION_CANCELLED -> "Производство отменено";
        };
    }

    public static String planningSource(MaterialPlanningSourceView source) {
        if (source == null) {
            return "—";
        }
        return switch (source) {
            case SPECIFICATION -> "Спецификация";
            case CUTTING_PLAN -> "Карта раскроя";
        };
    }

    public static String materialLineStatus(MaterialAvailabilityLineView line) {
        Objects.requireNonNull(line, "line");
        return switch (line.status()) {
            case AVAILABLE -> "Доступно";
            case INSUFFICIENT -> "Дефицит";
            case MATERIAL_UNRESOLVED -> "Материал не сопоставлен";
            case MATERIAL_AMBIGUOUS -> "Неоднозначное сопоставление материала";
        };
    }

    public static boolean isUnresolvedOrAmbiguous(MaterialAvailabilityLineStatus status) {
        return status == MaterialAvailabilityLineStatus.MATERIAL_UNRESOLVED
                || status == MaterialAvailabilityLineStatus.MATERIAL_AMBIGUOUS;
    }

    public static String cuttingPlanRefs(ItemProductionStateView state) {
        if (state == null || state.cuttingPlanLinks().isEmpty()) {
            return "—";
        }
        List<UUID> ids =
                state.cuttingPlanLinks().stream()
                        .map(link -> link.cuttingPlanId())
                        .distinct()
                        .collect(Collectors.toList());
        if (ids.size() == 1) {
            return "Карта раскроя: " + ids.get(0);
        }
        return "Карты раскроя: " + ids.size();
    }
}
