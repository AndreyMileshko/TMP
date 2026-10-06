package com.tmp.ui.shell.screen.warehouse;

import com.tmp.warehouse.api.WarehouseApi.PrepareProductionDemandTransfersResult;

/**
 * Human messages for «Подготовить перемещение» outcomes (B3B-3C3). Keeps result wording in one place.
 */
public final class WarehouseSupplyPrepareResultPresentation {

    public static final String STALE_TASK = "Задача уже обработана.";
    public static final String NOTHING_ROUTABLE = "Перемещение пока невозможно подготовить.";
    public static final String SINGLE_TRANSFER = "Перемещение подготовлено.";
    public static final String PARTIAL_SUFFIX =
            "Часть материалов пока ожидает обеспечения.";

    private WarehouseSupplyPrepareResultPresentation() {}

    public static String messageFor(PrepareProductionDemandTransfersResult result) {
        if (result == null) {
            return STALE_TASK;
        }
        int created = result.transfersCreated();
        int stillWaiting = result.linesStillWaiting();
        if (created <= 0 && stillWaiting <= 0) {
            return STALE_TASK;
        }
        if (created <= 0) {
            return NOTHING_ROUTABLE;
        }
        String prepared =
                created == 1
                        ? SINGLE_TRANSFER
                        : "Подготовлено перемещений: " + created + ".";
        if (stillWaiting > 0) {
            return prepared + "\n" + PARTIAL_SUFFIX;
        }
        return prepared;
    }

    public static boolean isTerminalSuccess(PrepareProductionDemandTransfersResult result) {
        return result != null
                && result.transfersCreated() > 0
                && result.linesStillWaiting() <= 0;
    }

    public static boolean isStaleOrEmpty(PrepareProductionDemandTransfersResult result) {
        return result == null
                || (result.transfersCreated() <= 0 && result.linesStillWaiting() <= 0);
    }

    public static boolean isPartial(PrepareProductionDemandTransfersResult result) {
        return result != null
                && result.transfersCreated() > 0
                && result.linesStillWaiting() > 0;
    }

    public static boolean isNothingRoutable(PrepareProductionDemandTransfersResult result) {
        return result != null
                && result.transfersCreated() <= 0
                && result.linesStillWaiting() > 0;
    }
}
