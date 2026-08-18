package com.tmp.warehouse.testsupport;

import com.tmp.security.api.AuthorizationService;
import com.tmp.warehouse.application.DefaultWarehouseApi;
import com.tmp.warehouse.application.FixedMaterialReferenceDisplayPort;
import com.tmp.warehouse.application.WarehouseAdjustmentService;
import com.tmp.warehouse.application.WarehouseConsumptionService;
import com.tmp.warehouse.application.WarehouseMoveService;
import com.tmp.warehouse.application.WarehouseOperationEngine;
import com.tmp.warehouse.application.WarehouseReceiptService;
import com.tmp.warehouse.application.WarehouseReservationLinkService;
import com.tmp.warehouse.application.WarehouseTransferService;
import com.tmp.warehouse.domain.repository.MaterialReferenceRepository;
import com.tmp.warehouse.domain.repository.StockPositionRepository;
import com.tmp.warehouse.domain.repository.TransferOperationContextRepository;
import com.tmp.warehouse.domain.repository.WarehouseCatalogRepository;
import com.tmp.warehouse.domain.repository.WarehouseOperationRepository;
import com.tmp.warehouse.persistence.JdbcMaterialReservationLinkRepository;
import com.tmp.warehouse.persistence.JdbcTransferOperationContextRepository;
import java.time.Clock;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Builds {@link DefaultWarehouseApi} for integration tests. */
public final class WarehouseIntegrationTestSupport {

    private WarehouseIntegrationTestSupport() {}

    public record ApiBundle(
            DefaultWarehouseApi api,
            JdbcTemplate jdbc,
            WarehouseOperationRepository operations,
            TransferOperationContextRepository transferContexts,
            MaterialReferenceRepository materials,
            StockPositionRepository stockPositions,
            WarehouseCatalogRepository catalog) {}

    public static ApiBundle createApiBundle(DataSource dataSource, Clock clock) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        var stockJdbc =
                new com.tmp.warehouse.persistence.JdbcWarehouseStockRepository(jdbc, clock);
        MaterialReferenceRepository materials =
                new com.tmp.warehouse.persistence.JdbcMaterialReferenceRepository(jdbc, clock);
        WarehouseCatalogRepository catalog =
                new com.tmp.warehouse.persistence.JdbcWarehouseCatalogRepository(jdbc, clock);
        WarehouseOperationRepository operations =
                new com.tmp.warehouse.persistence.JdbcWarehouseOperationRepository(stockJdbc, clock);
        TransferOperationContextRepository transferContexts =
                new JdbcTransferOperationContextRepository(jdbc);
        StockPositionRepository stockPositions =
                new com.tmp.warehouse.persistence.JdbcStockPositionRepository(stockJdbc);
        var movements = new com.tmp.warehouse.persistence.JdbcWarehouseMovementRepository(jdbc);
        WarehouseOperationEngine engine =
                new WarehouseOperationEngine(
                        operations,
                        stockPositions,
                        movements,
                        new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                        clock);
        DefaultWarehouseApi api =
                new DefaultWarehouseApi(
                        authorizationAllowAll(),
                        catalog,
                        stockPositions,
                        materials,
                        new FixedMaterialReferenceDisplayPort(),
                        new WarehouseReservationLinkService(
                                new JdbcMaterialReservationLinkRepository(jdbc), clock),
                        new WarehouseReceiptService(engine, stockPositions, materials),
                        new WarehouseMoveService(engine),
                        new WarehouseTransferService(engine, operations, transferContexts),
                        new WarehouseConsumptionService(engine, stockPositions),
                        new WarehouseAdjustmentService(engine, stockPositions),
                        operations,
                        transferContexts);
        return new ApiBundle(api, jdbc, operations, transferContexts, materials, stockPositions, catalog);
    }

    private static AuthorizationService authorizationAllowAll() {
        return new AuthorizationService() {
            @Override
            public boolean hasPermission(com.tmp.security.api.PermissionId permissionId) {
                return true;
            }

            @Override
            public void requirePermission(com.tmp.security.api.PermissionId permissionId) {}

            @Override
            public java.util.Set<com.tmp.security.api.PermissionId> effectivePermissions() {
                return java.util.Set.of();
            }
        };
    }
}
