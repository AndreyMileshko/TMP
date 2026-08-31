package com.tmp.ui.shell.screen.production;

import com.tmp.production.api.ProductionApplicationApi.WarehouseTransferRefView;
import com.tmp.warehouse.api.WarehouseApi.TransferStatusView;
import com.tmp.warehouse.api.WarehouseQueryApi;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Presentation helper: mirrors Warehouse lifecycle gating for receipt confirmation. */
final class TransferReceiptEligibility {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_RECEIVED = "RECEIVED";

    private TransferReceiptEligibility() {}

    static boolean isReceivable(
            List<WarehouseTransferRefView> warehouseOperations, WarehouseQueryApi warehouseQueryApi) {
        Objects.requireNonNull(warehouseOperations, "warehouseOperations");
        Objects.requireNonNull(warehouseQueryApi, "warehouseQueryApi");
        if (warehouseOperations.isEmpty()) {
            return false;
        }
        boolean anySent = false;
        boolean allReceived = true;
        for (WarehouseTransferRefView ref : warehouseOperations) {
            TransferStatusView status =
                    warehouseQueryApi.getTransferStatus(ref.warehouseDraftOperationId());
            String lifecycle = status.status();
            if (STATUS_DRAFT.equals(lifecycle)) {
                return false;
            }
            if (STATUS_SENT.equals(lifecycle)) {
                anySent = true;
                allReceived = false;
            } else if (!STATUS_RECEIVED.equals(lifecycle)) {
                allReceived = false;
            }
        }
        return anySent && !allReceived;
    }

    static String lifecycleSummary(
            List<WarehouseTransferRefView> warehouseOperations, WarehouseQueryApi warehouseQueryApi) {
        if (warehouseOperations.isEmpty()) {
            return "нет операций";
        }
        if (warehouseOperations.size() == 1) {
            UUID id = warehouseOperations.getFirst().warehouseDraftOperationId();
            return warehouseQueryApi.getTransferStatus(id).status();
        }
        int draft = 0;
        int sent = 0;
        int received = 0;
        for (WarehouseTransferRefView ref : warehouseOperations) {
            String lifecycle =
                    warehouseQueryApi.getTransferStatus(ref.warehouseDraftOperationId()).status();
            switch (lifecycle) {
                case STATUS_DRAFT -> draft++;
                case STATUS_SENT -> sent++;
                case STATUS_RECEIVED -> received++;
                default -> {
                    return lifecycle;
                }
            }
        }
        if (draft > 0) {
            return "DRAFT (" + draft + "/" + warehouseOperations.size() + ")";
        }
        if (sent > 0 && received > 0) {
            return "SENT+RECEIVED (" + sent + "+" + received + ")";
        }
        if (sent > 0) {
            return sent == warehouseOperations.size() ? "SENT" : "SENT (" + sent + ")";
        }
        if (received == warehouseOperations.size()) {
            return "RECEIVED";
        }
        return "UNKNOWN";
    }
}
