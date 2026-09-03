package com.tmp.ui.shell.screen.ordereditor;

import com.tmp.production.api.ProductionQueryApi;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class EmptyProductionQuery implements ProductionQueryApi {

    @Override
    public OrderProductionView getOrderProductionView(UUID orderId) {
        return new OrderProductionView(orderId, OrderProductionViewStatus.NOT_ACCEPTED, 0, 0, 0, 0, 0);
    }

    @Override
    public Optional<ItemProductionStateView> getItemProductionState(UUID orderItemId) {
        return Optional.empty();
    }

    @Override
    public Optional<MaterialAvailabilityResultView> getMaterialAvailabilityResult(UUID orderId) {
        return Optional.empty();
    }

    @Override
    public List<ProductionHistoryEntryView> listProductionHistory(UUID orderId) {
        return List.of();
    }
}
