package com.tmp.production.application;

import com.tmp.production.domain.MaterialRequirementSourceItemKey;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionStatus;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.repository.MaterialRequirementRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Application read/calculate boundary for Material Requirement product coverage. Single place for
 * the Stage 7 Phase 2 coverage formula used by prepare, submit and query APIs.
 */
public final class MaterialRequirementCoverageService {

    private final ProductionOrderViewService orderViewService;
    private final MaterialRequirementRepository requirementRepository;
    private final MaterialRequirementProductCoverageCalculator calculator;

    public MaterialRequirementCoverageService(
            ProductionOrderViewService orderViewService,
            MaterialRequirementRepository requirementRepository) {
        this(
                orderViewService,
                requirementRepository,
                new MaterialRequirementProductCoverageCalculator());
    }

    MaterialRequirementCoverageService(
            ProductionOrderViewService orderViewService,
            MaterialRequirementRepository requirementRepository,
            MaterialRequirementProductCoverageCalculator calculator) {
        this.orderViewService = Objects.requireNonNull(orderViewService, "orderViewService");
        this.requirementRepository =
                Objects.requireNonNull(requirementRepository, "requirementRepository");
        this.calculator = Objects.requireNonNull(calculator, "calculator");
    }

    public MaterialRequirementProductCoverageCalculator.ProductItemCoverage coverageFor(
            ProductionItemState state) {
        Objects.requireNonNull(state, "state");
        MaterialRequirementSourceItemKey key =
                MaterialRequirementSourceItemKey.of(
                        state.sourceOrderId(), state.sourceOrderItemId());
        long submitted =
                requirementRepository
                        .sumSubmittedProductQuantities(List.of(key))
                        .getOrDefault(key, 0L);
        return coverageFor(state, submitted);
    }

    public MaterialRequirementProductCoverageCalculator.ProductItemCoverage coverageFor(
            ProductionItemState state, long submittedProductCoverage) {
        Objects.requireNonNull(state, "state");
        if (state.status() == ProductionStatus.CANCELLED
                || state.activeProductionQuantity().isZero()) {
            return calculator.coverage(
                    toLong(state.orderedQuantity()),
                    0L,
                    toLong(state.releasedQuantity()),
                    submittedProductCoverage);
        }
        return calculator.coverage(
                toLong(state.orderedQuantity()),
                toLong(state.activeProductionQuantity()),
                toLong(state.releasedQuantity()),
                submittedProductCoverage);
    }

    public Map<MaterialRequirementSourceItemKey, MaterialRequirementProductCoverageCalculator.ProductItemCoverage>
            coverageForItems(Collection<MaterialRequirementSourceItemKey> keys) {
        Objects.requireNonNull(keys, "keys");
        Map<MaterialRequirementSourceItemKey, MaterialRequirementProductCoverageCalculator.ProductItemCoverage>
                result = new LinkedHashMap<>();
        if (keys.isEmpty()) {
            return result;
        }
        Map<MaterialRequirementSourceItemKey, Long> submitted =
                requirementRepository.sumSubmittedProductQuantities(keys);
        LinkedHashSet<SourceOrderId> orderIds = new LinkedHashSet<>();
        for (MaterialRequirementSourceItemKey key : keys) {
            orderIds.add(key.sourceOrderId());
        }
        Map<SourceOrderId, List<ProductionItemState>> statesByOrder = new LinkedHashMap<>();
        for (SourceOrderId orderId : orderIds) {
            statesByOrder.put(orderId, orderViewService.listItemStates(orderId));
        }
        for (MaterialRequirementSourceItemKey key : keys) {
            ProductionItemState state =
                    findState(statesByOrder.get(key.sourceOrderId()), key.sourceOrderItemId());
            if (state == null) {
                result.put(
                        key,
                        calculator.coverage(0L, 0L, 0L, submitted.getOrDefault(key, 0L)));
                continue;
            }
            result.put(key, coverageFor(state, submitted.getOrDefault(key, 0L)));
        }
        return Map.copyOf(result);
    }

    private static ProductionItemState findState(
            List<ProductionItemState> states, SourceOrderItemId itemId) {
        if (states == null) {
            return null;
        }
        for (ProductionItemState state : states) {
            if (state.sourceOrderItemId().equals(itemId)) {
                return state;
            }
        }
        return null;
    }

    private static long toLong(com.tmp.production.domain.ProductionQuantity quantity) {
        return quantity.value().longValueExact();
    }
}
