package com.tmp.ui.shell.screen.orderlist;

import com.tmp.order.api.OrderCustomerOptionDto;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.OrderWorklistCriteria;
import com.tmp.order.api.OrderWorklistQuery;
import com.tmp.order.api.OrderWorklistRowDto;
import com.tmp.production.api.ProductionQueryApi;
import com.tmp.production.api.ProductionQueryApi.OrderProductionListFacts;
import com.tmp.production.api.ProductionQueryApi.OrderProductionView;
import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.Login;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.SessionId;
import com.tmp.security.api.SessionSummary;
import com.tmp.security.api.UserId;
import com.tmp.security.api.UserUiPreferenceService;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.order.worklist.OrderOperationalListService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OrderListTestSupport {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-03T10:00:00Z"), ZoneOffset.UTC);

    private OrderListTestSupport() {}

    static OrderListViewModel viewModel() {
        return viewModel(new FakeAuthorization(
                PermissionId.of(UiShellScreens.ORDER_LIST_REQUIRED_PERMISSION),
                PermissionId.of(UiShellScreens.ORDER_CREATE_PERMISSION)));
    }

    static OrderListViewModel viewModel(AuthorizationService authorization) {
        return viewModel(
                new InMemoryWorklistQuery(),
                new MapProductionQuery(),
                authorization,
                new SessionAuthn(userId()),
                new InMemoryPreferences());
    }

    static OrderListViewModel viewModel(
            InMemoryWorklistQuery worklist,
            MapProductionQuery production,
            AuthorizationService authorization,
            AuthenticationService authentication,
            UserUiPreferenceService preferences) {
        return new OrderListViewModel(
                new OrderOperationalListService(worklist, production),
                worklist,
                authorization,
                authentication,
                preferences,
                CLOCK);
    }

    static UserId userId() {
        return UserId.of(UUID.fromString("11111111-1111-4111-8111-111111111111"));
    }

    static UserId userId(String value) {
        return UserId.of(UUID.fromString(value));
    }

    static OrderWorklistRowDto row(
            String number, OrderStatus status, String customerRef, String customerName, Instant createdAt) {
        return OrderWorklistRowDto.of(
                OrderId.generate(), number, status, customerRef, customerName, createdAt, 1L);
    }

    static OrderWorklistRowDto row(
            OrderId id,
            String number,
            OrderStatus status,
            String customerRef,
            String customerName,
            Instant createdAt,
            long itemQuantity) {
        return OrderWorklistRowDto.of(
                id, number, status, customerRef, customerName, createdAt, itemQuantity);
    }

    public static class InMemoryWorklistQuery implements OrderWorklistQuery {
        public final List<OrderWorklistRowDto> rows = new ArrayList<>();
        public OrderWorklistCriteria lastCriteria;
        public int listCalls;
        public int knownCustomerCalls;
        public RuntimeException deny;
        public RuntimeException denyCustomers;

        @Override
        public List<OrderWorklistRowDto> listWorklistRows(OrderWorklistCriteria criteria) {
            if (deny != null) {
                throw deny;
            }
            listCalls++;
            lastCriteria = criteria;
            List<OrderWorklistRowDto> matched = new ArrayList<>();
            for (OrderWorklistRowDto row : rows) {
                if (row.createdAt().isBefore(criteria.createdFrom())
                        || !row.createdAt().isBefore(criteria.createdToExclusive())) {
                    continue;
                }
                if (criteria.quickSearch().isPresent()) {
                    String q = criteria.quickSearch().get().toLowerCase(Locale.ROOT);
                    boolean numberMatch = row.orderNumber().toLowerCase(Locale.ROOT).contains(q);
                    boolean nameMatch =
                            row.customerName() != null
                                    && row.customerName().toLowerCase(Locale.ROOT).contains(q);
                    if (!numberMatch && !nameMatch) {
                        continue;
                    }
                }
                if (criteria.filterByCustomers()) {
                    boolean match = false;
                    if (row.customerRef() != null
                            && !row.customerRef().isBlank()
                            && criteria.customerRefs().contains(row.customerRef())) {
                        match = true;
                    }
                    boolean blankName =
                            row.customerName() == null || row.customerName().isBlank();
                    if (row.customerRef() == null || row.customerRef().isBlank()) {
                        if (!blankName) {
                            String normalized = row.customerName().trim();
                            for (String name : criteria.customerNames()) {
                                if (normalized.equals(name)) {
                                    match = true;
                                    break;
                                }
                            }
                        } else if (criteria.includeUnassignedCustomer()) {
                            match = true;
                        }
                    }
                    if (!match) {
                        continue;
                    }
                }
                matched.add(row);
            }
            return matched;
        }

        @Override
        public List<OrderCustomerOptionDto> listKnownCustomers() {
            if (denyCustomers != null) {
                throw denyCustomers;
            }
            knownCustomerCalls++;
            Map<String, OrderWorklistRowDto> latestByRef = new LinkedHashMap<>();
            Map<String, OrderWorklistRowDto> latestByName = new LinkedHashMap<>();
            boolean unassigned = false;
            for (OrderWorklistRowDto row : rows) {
                boolean blankRef = row.customerRef() == null || row.customerRef().isBlank();
                boolean blankName = row.customerName() == null || row.customerName().isBlank();
                if (blankRef && blankName) {
                    unassigned = true;
                    continue;
                }
                if (blankRef) {
                    String nameKey = row.customerName().trim();
                    OrderWorklistRowDto previous = latestByName.get(nameKey);
                    if (previous == null
                            || row.createdAt().isAfter(previous.createdAt())
                            || (row.createdAt().equals(previous.createdAt())
                                    && row.orderId()
                                            .value()
                                            .compareTo(previous.orderId().value())
                                            > 0)) {
                        latestByName.put(nameKey, row);
                    }
                    continue;
                }
                OrderWorklistRowDto previous = latestByRef.get(row.customerRef());
                if (previous == null
                        || row.createdAt().isAfter(previous.createdAt())
                        || (row.createdAt().equals(previous.createdAt())
                                && row.orderId()
                                        .value()
                                        .compareTo(previous.orderId().value())
                                        > 0)) {
                    latestByRef.put(row.customerRef(), row);
                }
            }
            List<OrderCustomerOptionDto> options = new ArrayList<>();
            for (OrderWorklistRowDto row : latestByRef.values()) {
                options.add(OrderCustomerOptionDto.of(row.customerRef(), row.customerName()));
            }
            options.sort((a, b) -> {
                String nameA = a.customerName() == null ? "" : a.customerName();
                String nameB = b.customerName() == null ? "" : b.customerName();
                int byName = nameA.compareToIgnoreCase(nameB);
                if (byName != 0) {
                    return byName;
                }
                String refA = a.customerRef() == null ? "" : a.customerRef();
                String refB = b.customerRef() == null ? "" : b.customerRef();
                return refA.compareTo(refB);
            });
            List<OrderCustomerOptionDto> nameOptions = new ArrayList<>();
            for (OrderWorklistRowDto row : latestByName.values()) {
                nameOptions.add(OrderCustomerOptionDto.legacyName(row.customerName()));
            }
            nameOptions.sort((a, b) -> a.customerName().compareToIgnoreCase(b.customerName()));
            options.addAll(nameOptions);
            if (unassigned) {
                options.add(0, OrderCustomerOptionDto.unassigned());
            }
            return options;
        }
    }

    public static class MapProductionQuery implements ProductionQueryApi {
        public final Map<UUID, OrderProductionListFacts> facts = new LinkedHashMap<>();
        public int batchCalls;
        public int viewCalls;

        public void put(OrderId orderId, OrderProductionListFacts value) {
            facts.put(orderId.value(), value);
        }

        @Override
        public OrderProductionView getOrderProductionView(UUID orderId) {
            viewCalls++;
            OrderProductionListFacts fact = facts.get(orderId);
            OrderProductionViewStatus status =
                    fact == null ? OrderProductionViewStatus.NOT_ACCEPTED : fact.status();
            return new OrderProductionView(orderId, status, 0, 0, 0, 0, 0);
        }

        @Override
        public Map<UUID, OrderProductionListFacts> getOrderProductionListFacts(Collection<UUID> orderIds) {
            batchCalls++;
            Map<UUID, OrderProductionListFacts> result = new LinkedHashMap<>();
            for (UUID id : orderIds) {
                OrderProductionListFacts fact = facts.get(id);
                if (fact != null) {
                    result.put(id, fact);
                } else {
                    result.put(
                            id,
                            new OrderProductionListFacts(
                                    id, OrderProductionViewStatus.NOT_ACCEPTED, 0L, 0L, 0L, false));
                }
            }
            return result;
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

    public static final class InMemoryPreferences implements UserUiPreferenceService {
        private final Map<String, String> values = new ConcurrentHashMap<>();
        public int saveCalls;

        @Override
        public Optional<String> load(UserId userId, String namespace, String preferenceKey) {
            return Optional.ofNullable(values.get(key(userId, namespace, preferenceKey)));
        }

        @Override
        public void save(
                UserId userId, String namespace, String preferenceKey, int preferenceVersion, String value) {
            saveCalls++;
            values.put(key(userId, namespace, preferenceKey), value);
        }

        private static String key(UserId userId, String namespace, String preferenceKey) {
            return userId.value() + "|" + namespace + "|" + preferenceKey;
        }
    }

    public static final class SessionAuthn implements AuthenticationService {
        private SessionSummary session;

        SessionAuthn(UserId userId) {
            this.session =
                    new SessionSummary(
                            SessionId.of(UUID.randomUUID()),
                            userId,
                            Login.of("tester"),
                            Instant.parse("2026-09-03T09:00:00Z"));
        }

        void setUser(UserId userId) {
            this.session =
                    new SessionSummary(
                            SessionId.of(UUID.randomUUID()),
                            userId,
                            Login.of("tester"),
                            Instant.parse("2026-09-03T09:00:00Z"));
        }

        void clear() {
            session = null;
        }

        @Override
        public SessionSummary login(Login login, char[] password) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SessionSummary completePasswordSetup(
                Login login, String activationCode, char[] newPassword, char[] confirmPassword) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void logout() {
            session = null;
        }

        @Override
        public Optional<SessionSummary> currentSession() {
            return Optional.ofNullable(session);
        }

        @Override
        public boolean isAuthenticated() {
            return session != null;
        }
    }
}
