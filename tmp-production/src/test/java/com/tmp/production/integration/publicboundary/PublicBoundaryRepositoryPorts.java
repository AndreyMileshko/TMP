package com.tmp.production.integration.publicboundary;

import com.tmp.production.domain.ProductionHistoryEntry;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.domain.repository.ProductionHistoryRepository;
import com.tmp.production.domain.repository.ProductionItemStateRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Static forwarding ports so Document Engine processors registered once always see the current
 * controllable JDBC repositories used by the active test composition.
 */
final class PublicBoundaryRepositoryPorts {

    private static volatile ProductionItemStateRepository itemStates;
    private static volatile ProductionHistoryRepository history;

    private PublicBoundaryRepositoryPorts() {}

    static void bind(
            ProductionItemStateRepository itemStateRepository,
            ProductionHistoryRepository historyRepository) {
        itemStates = Objects.requireNonNull(itemStateRepository, "itemStateRepository");
        history = Objects.requireNonNull(historyRepository, "historyRepository");
    }

    static ProductionItemStateRepository itemStates() {
        return ITEM_STATES_PORT;
    }

    static ProductionHistoryRepository history() {
        return HISTORY_PORT;
    }

    private static final ProductionItemStateRepository ITEM_STATES_PORT =
            new ProductionItemStateRepository() {
                @Override
                public ProductionItemState save(ProductionItemState state) {
                    return requireItems().save(state);
                }

                @Override
                public Optional<ProductionItemState> findByIdentity(
                        SourceOrderId sourceOrderId,
                        SourceOrderItemId sourceOrderItemId,
                        SpecificationId specificationId) {
                    return requireItems().findByIdentity(sourceOrderId, sourceOrderItemId, specificationId);
                }

                @Override
                public List<ProductionItemState> findBySourceOrderId(SourceOrderId sourceOrderId) {
                    return requireItems().findBySourceOrderId(sourceOrderId);
                }

                @Override
                public Optional<ProductionItemState> findBySourceOrderItemId(
                        SourceOrderItemId sourceOrderItemId) {
                    return requireItems().findBySourceOrderItemId(sourceOrderItemId);
                }

                @Override
                public List<ProductionItemState> findBySourceOrderIdForUpdate(
                        SourceOrderId sourceOrderId) {
                    return requireItems().findBySourceOrderIdForUpdate(sourceOrderId);
                }
            };

    private static final ProductionHistoryRepository HISTORY_PORT =
            new ProductionHistoryRepository() {
                @Override
                public ProductionHistoryEntry append(ProductionHistoryEntry entry) {
                    return requireHistory().append(entry);
                }

                @Override
                public List<ProductionHistoryEntry> listByOrder(SourceOrderId sourceOrderId) {
                    return requireHistory().listByOrder(sourceOrderId);
                }
            };

    private static ProductionItemStateRepository requireItems() {
        ProductionItemStateRepository current = itemStates;
        if (current == null) {
            throw new IllegalStateException("Production item state repository port is not bound");
        }
        return current;
    }

    private static ProductionHistoryRepository requireHistory() {
        ProductionHistoryRepository current = history;
        if (current == null) {
            throw new IllegalStateException("Production history repository port is not bound");
        }
        return current;
    }
}
