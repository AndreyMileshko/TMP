package com.tmp.production.application;

import com.tmp.production.domain.MaterialAvailabilityCheckResult;
import com.tmp.production.domain.SourceOrderId;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Explicit material availability check use case for an order in {@code IN_PRODUCTION}.
 *
 * <p>Delegates calculation to the authoritative {@link CurrentMaterialAvailabilityQueryService},
 * then appends {@code MATERIALS_CHECKED} history in a short REQUIRED transaction. Does not mutate
 * Warehouse stock or Production item state.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores injected calculator, history service and TX manager.")
public final class CheckMaterialAvailabilityService {

    private final CurrentMaterialAvailabilityQueryService calculator;
    private final ProductionHistoryService historyService;
    private final TransactionTemplate transactionTemplate;

    public CheckMaterialAvailabilityService(
            CurrentMaterialAvailabilityQueryService calculator,
            ProductionHistoryService historyService,
            PlatformTransactionManager transactionManager) {
        this.calculator = Objects.requireNonNull(calculator, "calculator");
        this.historyService = Objects.requireNonNull(historyService, "historyService");
        this.transactionTemplate =
                new TransactionTemplate(
                        Objects.requireNonNull(transactionManager, "transactionManager"));
    }

    /**
     * Compatibility constructor used by existing unit tests that wire calculator dependencies
     * directly. Prefer injecting a shared {@link CurrentMaterialAvailabilityQueryService} bean.
     */
    public CheckMaterialAvailabilityService(
            ProductionOrderViewService orderViewService,
            ProductionFoundationQueryService foundationQuery,
            com.tmp.production.application.port.WarehouseAvailabilityQueryPort warehouseQuery,
            ProductionWarehouseScope warehouseScope,
            ProductionHistoryService historyService,
            PlatformTransactionManager transactionManager,
            java.time.Clock clock) {
        this(
                new CurrentMaterialAvailabilityQueryService(
                        orderViewService, foundationQuery, warehouseQuery, warehouseScope, clock),
                historyService,
                transactionManager);
    }

    CheckMaterialAvailabilityService(
            ProductionOrderViewService orderViewService,
            ProductionFoundationQueryService foundationQuery,
            com.tmp.production.application.port.WarehouseAvailabilityQueryPort warehouseQuery,
            ProductionWarehouseScope warehouseScope,
            SpecificationMaterialRequirementCalculator requirementCalculator,
            MaterialReferenceResolver materialReferenceResolver,
            ProductionHistoryService historyService,
            PlatformTransactionManager transactionManager,
            java.time.Clock clock) {
        this(
                new CurrentMaterialAvailabilityQueryService(
                        orderViewService,
                        foundationQuery,
                        warehouseQuery,
                        warehouseScope,
                        requirementCalculator,
                        materialReferenceResolver,
                        clock),
                historyService,
                transactionManager);
    }

    public MaterialAvailabilityCheckResult check(SourceOrderId sourceOrderId) {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        MaterialAvailabilityCheckResult result = calculator.evaluate(sourceOrderId);
        transactionTemplate.executeWithoutResult(
                status -> historyService.append(historyService.materialsChecked(result)));
        return result;
    }
}
