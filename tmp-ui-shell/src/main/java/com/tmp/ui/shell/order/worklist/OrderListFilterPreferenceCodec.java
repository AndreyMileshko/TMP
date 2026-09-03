package com.tmp.ui.shell.order.worklist;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Closed-schema encoder for {@link OrderListFilterPreference}. Invalid or stale values fall back
 * to defaults instead of failing the Orders screen.
 */
public final class OrderListFilterPreferenceCodec {

    private OrderListFilterPreferenceCodec() {}

    public static String encode(OrderListFilterPreference preference) {
        StringJoiner statuses = new StringJoiner(",");
        for (OrderOperationalStatus status : preference.statuses()) {
            statuses.add(status.name());
        }
        StringJoiner customers = new StringJoiner(",");
        for (String ref : preference.customerRefs()) {
            customers.add(escape(ref));
        }
        return "v="
                + OrderListFilterPreference.VERSION
                + ";statuses="
                + statuses
                + ";allCustomers="
                + preference.selectAllCustomers()
                + ";customers="
                + customers
                + ";unassigned="
                + preference.includeUnassignedCustomer()
                + ";period="
                + preference.periodPreset().name()
                + ";from="
                + (preference.customFrom() == null ? "" : preference.customFrom())
                + ";to="
                + (preference.customTo() == null ? "" : preference.customTo())
                + ";pageSize="
                + preference.pageSize();
    }

    public static OrderListFilterPreference decode(String raw) {
        OrderListFilterPreference defaults = OrderListFilterPreference.defaults();
        if (raw == null || raw.isBlank()) {
            return defaults;
        }
        try {
            Parsed parsed = parse(raw);
            if (parsed.version != OrderListFilterPreference.VERSION) {
                return defaults;
            }
            Set<OrderOperationalStatus> statuses = new LinkedHashSet<>();
            if (parsed.statuses.isEmpty()) {
                statuses.addAll(defaults.statuses());
            } else {
                for (String token : parsed.statuses.split(",")) {
                    if (token.isBlank()) {
                        continue;
                    }
                    try {
                        statuses.add(OrderOperationalStatus.valueOf(token.trim().toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException ignored) {
                        // stale enum — skip
                    }
                }
            }
            if (statuses.isEmpty()) {
                statuses.addAll(defaults.statuses());
            }
            OrderListPeriod.Preset preset;
            try {
                preset = OrderListPeriod.Preset.valueOf(parsed.period);
            } catch (RuntimeException ex) {
                preset = defaults.periodPreset();
            }
            LocalDate from = parseDate(parsed.from);
            LocalDate to = parseDate(parsed.to);
            if (preset == OrderListPeriod.Preset.CUSTOM && (from == null || to == null || from.isAfter(to))) {
                preset = defaults.periodPreset();
                from = null;
                to = null;
            }
            int pageSize = parsed.pageSize < 1 ? defaults.pageSize() : parsed.pageSize;
            Set<String> refs = new LinkedHashSet<>();
            if (!parsed.customers.isEmpty()) {
                for (String token : splitCustomers(parsed.customers)) {
                    if (!token.isBlank()) {
                        refs.add(token);
                    }
                }
            }
            return new OrderListFilterPreference(
                    statuses,
                    parsed.allCustomers,
                    refs,
                    parsed.unassigned,
                    preset,
                    from,
                    to,
                    pageSize);
        } catch (RuntimeException ex) {
            return defaults;
        }
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,");
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder();
        boolean escape = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escape) {
                out.append(ch);
                escape = false;
            } else if (ch == '\\') {
                escape = true;
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    private static java.util.List<String> splitCustomers(String raw) {
        java.util.ArrayList<String> tokens = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escape = false;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (escape) {
                current.append(ch);
                escape = false;
            } else if (ch == '\\') {
                escape = true;
            } else if (ch == ',') {
                tokens.add(unescape(current.toString()));
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        tokens.add(unescape(current.toString()));
        return tokens;
    }

    private static Parsed parse(String raw) {
        Parsed parsed = new Parsed();
        StringBuilder key = new StringBuilder();
        StringBuilder value = new StringBuilder();
        boolean inValue = false;
        boolean escape = false;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (escape) {
                (inValue ? value : key).append(ch);
                escape = false;
                continue;
            }
            if (ch == '\\') {
                escape = true;
                continue;
            }
            if (!inValue && ch == '=') {
                inValue = true;
                continue;
            }
            if (ch == ';') {
                apply(parsed, key.toString(), value.toString());
                key.setLength(0);
                value.setLength(0);
                inValue = false;
                continue;
            }
            (inValue ? value : key).append(ch);
        }
        if (!key.isEmpty()) {
            apply(parsed, key.toString(), value.toString());
        }
        return parsed;
    }

    private static void apply(Parsed parsed, String key, String value) {
        switch (key) {
            case "v" -> parsed.version = Integer.parseInt(value);
            case "statuses" -> parsed.statuses = value;
            case "allCustomers" -> parsed.allCustomers = Boolean.parseBoolean(value);
            case "customers" -> parsed.customers = value;
            case "unassigned" -> parsed.unassigned = Boolean.parseBoolean(value);
            case "period" -> parsed.period = value;
            case "from" -> parsed.from = value;
            case "to" -> parsed.to = value;
            case "pageSize" -> parsed.pageSize = Integer.parseInt(value);
            default -> {
                // ignore unknown keys
            }
        }
    }

    private static final class Parsed {
        private int version;
        private String statuses = "";
        private boolean allCustomers = true;
        private String customers = "";
        private boolean unassigned;
        private String period = OrderListPeriod.Preset.LAST_30_DAYS.name();
        private String from = "";
        private String to = "";
        private int pageSize = 50;
    }
}
