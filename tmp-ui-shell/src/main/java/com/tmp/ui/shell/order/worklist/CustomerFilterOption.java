package com.tmp.ui.shell.order.worklist;

import com.tmp.order.api.OrderCustomerOptionDto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One user-visible customer-filter row. May represent a union of keys when a stable ref and a
 * legacy null-ref name share the same display string.
 */
public final class CustomerFilterOption {

    private final String displayName;
    private final Set<CustomerFilterKey> keys;

    public CustomerFilterOption(String displayName, Set<CustomerFilterKey> keys) {
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        if (keys == null || keys.isEmpty()) {
            throw new IllegalArgumentException("keys must not be empty");
        }
        this.keys = Set.copyOf(keys);
    }

    public String displayName() {
        return displayName;
    }

    public Set<CustomerFilterKey> keys() {
        return keys;
    }

    public boolean containsAny(Set<CustomerFilterKey> selected) {
        if (selected == null || selected.isEmpty()) {
            return false;
        }
        for (CustomerFilterKey key : keys) {
            if (selected.contains(key)) {
                return true;
            }
        }
        return false;
    }

    public static List<CustomerFilterOption> compose(List<OrderCustomerOptionDto> dtos) {
        List<OrderCustomerOptionDto> source = dtos == null ? List.of() : dtos;
        OrderCustomerOptionDto unassigned = null;
        List<OrderCustomerOptionDto> refs = new ArrayList<>();
        List<OrderCustomerOptionDto> names = new ArrayList<>();
        for (OrderCustomerOptionDto dto : source) {
            if (dto == null) {
                continue;
            }
            if (dto.isUnassigned()) {
                unassigned = dto;
            } else if (dto.isLegacyName()) {
                names.add(dto);
            } else if (dto.customerRef() != null && !dto.customerRef().isBlank()) {
                refs.add(dto);
            }
        }

        Map<String, Integer> refDisplayCounts = new LinkedHashMap<>();
        for (OrderCustomerOptionDto ref : refs) {
            String display = namedDisplay(ref);
            refDisplayCounts.merge(display, 1, Integer::sum);
        }

        Map<String, OrderCustomerOptionDto> namesByDisplay = new LinkedHashMap<>();
        for (OrderCustomerOptionDto name : names) {
            namesByDisplay.putIfAbsent(namedDisplay(name), name);
        }

        List<CustomerFilterOption> result = new ArrayList<>();
        if (unassigned != null) {
            result.add(new CustomerFilterOption("Без заказчика", Set.of(CustomerFilterKey.unassigned())));
        }

        Set<String> consumedNameDisplays = new LinkedHashSet<>();
        for (OrderCustomerOptionDto ref : refs) {
            String display = namedDisplay(ref);
            Set<CustomerFilterKey> keys = new LinkedHashSet<>();
            keys.add(CustomerFilterKey.ref(ref.customerRef()));
            if (refDisplayCounts.getOrDefault(display, 0) == 1) {
                OrderCustomerOptionDto legacy = namesByDisplay.get(display);
                if (legacy != null) {
                    keys.add(CustomerFilterKey.name(legacy.customerName()));
                    consumedNameDisplays.add(display);
                }
            }
            result.add(new CustomerFilterOption(display, keys));
        }
        for (OrderCustomerOptionDto name : names) {
            String display = namedDisplay(name);
            if (consumedNameDisplays.contains(display)) {
                continue;
            }
            result.add(
                    new CustomerFilterOption(
                            display, Set.of(CustomerFilterKey.name(name.customerName()))));
        }
        return List.copyOf(result);
    }

    public static int selectedDisplayCount(
            List<CustomerFilterOption> options, Set<CustomerFilterKey> selected) {
        int count = 0;
        for (CustomerFilterOption option : options) {
            if (option.containsAny(selected)) {
                count++;
            }
        }
        return count;
    }

    public static Set<CustomerFilterKey> knownKeys(List<CustomerFilterOption> options) {
        Set<CustomerFilterKey> known = new LinkedHashSet<>();
        for (CustomerFilterOption option : options) {
            known.addAll(option.keys());
        }
        return Set.copyOf(known);
    }

    private static String namedDisplay(OrderCustomerOptionDto option) {
        if (option.customerName() == null || option.customerName().isBlank()) {
            return "—";
        }
        return option.customerName().trim();
    }
}
