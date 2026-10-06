package com.tmp.ui.shell.screen.warehouse;

/**
 * Human labels for Warehouse Demand waiting reasons (B3B-3C1). Enum names must not appear in UI.
 */
public final class WarehouseDemandWaitingReasonPresentation {

    private WarehouseDemandWaitingReasonPresentation() {}

    public static String labelFor(String effectiveWaitingReason) {
        if (effectiveWaitingReason == null || effectiveWaitingReason.isBlank()) {
            return "Ожидает подготовки перемещения";
        }
        return switch (effectiveWaitingReason.trim()) {
            case "MATERIAL_UNMATCHED" -> "Материал не сопоставлен со складскими данными";
            case "MATERIAL_AMBIGUOUS" -> "Материал определён неоднозначно";
            case "NO_AVAILABLE_STOCK" -> "Нет доступного остатка";
            case "ROUTING_DEFERRED" -> "Ожидает подготовки перемещения";
            default -> "Ожидает подготовки перемещения";
        };
    }
}
