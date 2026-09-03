package com.tmp.ui.shell.order.worklist;

import com.tmp.order.api.OrderWorklistCriteria;
import com.tmp.order.api.OrderWorklistQuery;
import com.tmp.order.api.OrderWorklistRowDto;
import com.tmp.production.api.ProductionQueryApi;
import com.tmp.production.api.ProductionQueryApi.OrderProductionListFacts;
import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import com.tmp.security.api.AccessDeniedException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Integration read for the operational Orders list: Order Management commercial rows plus a
 * Production batch, then status filter, then pagination.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Holds injected public query APIs managed by the container.")
public final class OrderOperationalListService {

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
                        .includeUnassignedCustomer(request.includeUnassignedCustomer())
                        .filterByCustomers(request.filterByCustomers())
                        .build();
        List<OrderWorklistRowDto> rows = worklistQuery.listWorklistRows(criteria);
        Map<UUID, OrderProductionListFacts> facts = loadProductionFacts(rows);
        List<OrderOperationalSummary> matched = new ArrayList<>();
        for (OrderWorklistRowDto row : rows) {
            OrderProductionListFacts production = facts.get(row.orderId().value());
            OrderOperationalStatus status =
                    OrderOperationalStatusDeriver.derive(row.status(), row.itemQuantity(), production);
            if (!request.statuses().contains(status)) {
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
            return new OrderOperationalListResult(List.of(), request.pageIndex(), request.pageSize(), total);
        }
        int to = Math.min(from + request.pageSize(), matched.size());
        return new OrderOperationalListResult(
                matched.subList(from, to), request.pageIndex(), request.pageSize(), total);
    }

    private Map<UUID, OrderProductionListFacts> loadProductionFacts(List<OrderWorklistRowDto> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = rows.stream().map(row -> row.orderId().value()).toList();
        try {
            Map<UUID, OrderProductionListFacts> loaded =
                    productionQueryApi.getOrderProductionListFacts(ids);
            Map<UUID, OrderProductionListFacts> complete = new LinkedHashMap<>(loaded);
            for (UUID id : ids) {
                complete.putIfAbsent(id, emptyFacts(id));
            }
            return complete;
        } catch (AccessDeniedException ex) {
            Map<UUID, OrderProductionListFacts> empty = new LinkedHashMap<>();
            for (UUID id : ids) {
                empty.put(id, emptyFacts(id));
            }
            return empty;
        }
    }

    private static OrderProductionListFacts emptyFacts(UUID orderId) {
        return new OrderProductionListFacts(
                orderId, OrderProductionViewStatus.NOT_ACCEPTED, 0L, 0L, 0L, false);
    }
}
