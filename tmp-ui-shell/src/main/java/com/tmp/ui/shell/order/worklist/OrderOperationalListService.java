package com.tmp.ui.shell.order.worklist;

import com.tmp.order.api.OrderWorklistCriteria;
import com.tmp.order.api.OrderWorklistQuery;
import com.tmp.order.api.OrderWorklistRowDto;
import com.tmp.production.api.ProductionQueryApi;
import com.tmp.production.api.ProductionQueryApi.OrderProductionListFacts;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.ui.shell.order.worklist.OrderOperationalListResult.ProductionFactsState;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Integration read for the operational Orders list: Order Management commercial rows plus a
 * Production batch, then status filter, then pagination.
 *
 * <p>Production read failures never become fake zero facts. Rows that need Production data surface
 * {@link OrderOperationalStatus#STATUS_UNAVAILABLE} and remain visible.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Holds injected public query APIs managed by the container.")
public final class OrderOperationalListService {

    private static final Logger LOGGER = System.getLogger(OrderOperationalListService.class.getName());

    private final OrderWorklistQuery worklistQuery;
    private final ProductionQueryApi productionQueryApi;

    public OrderOperationalListService(
            OrderWorklistQuery worklistQuery, ProductionQueryApi productionQueryApi) {
        this.worklistQuery = Objects.requireNonNull(worklistQuery, "worklistQuery");
        this.productionQueryApi = Objects.requireNonNull(productionQueryApi, "productionQueryApi");
    }

    public OrderOperationalListResult search(OrderOperationalListRequest request) {
        Objects.requireNonNull(request, "request");
        OrderWorklistCriteria criteria =
                OrderWorklistCriteria.builder()
                        .createdFrom(request.createdFrom())
                        .createdToExclusive(request.createdToExclusive())
                        .quickSearch(request.quickSearch())
                        .customerRefs(request.customerRefs())
                        .customerNames(request.customerNames())
                        .includeUnassignedCustomer(request.includeUnassignedCustomer())
                        .filterByCustomers(request.filterByCustomers())
                        .build();
        List<OrderWorklistRowDto> rows = worklistQuery.listWorklistRows(criteria);
        ProductionFactsLoad load = loadProductionFacts(rows);
        List<OrderOperationalSummary> matched = new ArrayList<>();
        for (OrderWorklistRowDto row : rows) {
            Optional<OrderProductionListFacts> production =
                    Optional.ofNullable(load.facts().get(row.orderId().value()));
            OrderOperationalStatus status =
                    OrderOperationalStatusDeriver.derive(
                            row.status(), row.itemQuantity(), production);
            if (!matchesStatusFilter(request, status)) {
                continue;
            }
            matched.add(
                    new OrderOperationalSummary(
                            row.orderId(),
                            row.orderNumber(),
                            row.customerRef(),
                            row.customerName(),
                            row.createdAt(),
                            row.itemQuantity(),
                            row.status(),
                            status));
        }
        long total = matched.size();
        int from = request.pageIndex() * request.pageSize();
        if (from >= matched.size()) {
            return new OrderOperationalListResult(
                    List.of(),
                    request.pageIndex(),
                    request.pageSize(),
                    total,
                    load.state(),
                    load.technicalFailure());
        }
        int to = Math.min(from + request.pageSize(), matched.size());
        return new OrderOperationalListResult(
                matched.subList(from, to),
                request.pageIndex(),
                request.pageSize(),
                total,
                load.state(),
                load.technicalFailure());
    }

    /**
     * Unavailable production status is always included so AccessDenied/technical failure cannot
     * silently hide commercial rows. User filter checkboxes do not offer STATUS_UNAVAILABLE.
     */
    private static boolean matchesStatusFilter(
            OrderOperationalListRequest request, OrderOperationalStatus status) {
        if (status == OrderOperationalStatus.STATUS_UNAVAILABLE) {
            return true;
        }
        return request.statuses().contains(status);
    }

    private ProductionFactsLoad loadProductionFacts(List<OrderWorklistRowDto> rows) {
        if (rows.isEmpty()) {
            return ProductionFactsLoad.available(Map.of());
        }
        List<UUID> ids = rows.stream().map(row -> row.orderId().value()).toList();
        try {
            Map<UUID, OrderProductionListFacts> loaded =
                    productionQueryApi.getOrderProductionListFacts(ids);
            Map<UUID, OrderProductionListFacts> complete = new LinkedHashMap<>();
            for (UUID id : ids) {
                OrderProductionListFacts fact = loaded.get(id);
                if (fact != null) {
                    complete.put(id, fact);
                }
                // Missing entry after a successful call is unavailable — not fake zeros.
            }
            return ProductionFactsLoad.available(complete);
        } catch (AccessDeniedException ex) {
            LOGGER.log(Level.DEBUG, "Production list facts denied; statuses marked unavailable");
            return ProductionFactsLoad.accessDenied();
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Production list facts failed; statuses marked unavailable", ex);
            return ProductionFactsLoad.technicalFailure(ex);
        }
    }

    private record ProductionFactsLoad(
            Map<UUID, OrderProductionListFacts> facts,
            ProductionFactsState state,
            RuntimeException technicalFailure) {

        static ProductionFactsLoad available(Map<UUID, OrderProductionListFacts> facts) {
            return new ProductionFactsLoad(facts, ProductionFactsState.AVAILABLE, null);
        }

        static ProductionFactsLoad accessDenied() {
            return new ProductionFactsLoad(Map.of(), ProductionFactsState.ACCESS_DENIED, null);
        }

        static ProductionFactsLoad technicalFailure(RuntimeException ex) {
            return new ProductionFactsLoad(Map.of(), ProductionFactsState.TECHNICAL_FAILURE, ex);
        }
    }
}
