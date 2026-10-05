package com.tmp.ui.shell.screen.production;

import com.tmp.ui.shell.order.worklist.OrderListPeriod;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Closed-schema encoder for {@link ProductionWorkbenchFilterPreference}. Invalid or stale values
 * fall back to defaults.
 */
public final class ProductionWorkbenchFilterPreferenceCodec {

    private ProductionWorkbenchFilterPreferenceCodec() {}

    public static String encode(ProductionWorkbenchFilterPreference preference) {
        return "v="
                + ProductionWorkbenchFilterPreference.VERSION
                + ";period="
                + preference.periodPreset().name()
                + ";from="
                + (preference.customFrom() == null ? "" : preference.customFrom())
                + ";to="
                + (preference.customTo() == null ? "" : preference.customTo());
    }

    public static ProductionWorkbenchFilterPreference decode(String raw) {
        ProductionWorkbenchFilterPreference defaults =
                ProductionWorkbenchFilterPreference.defaults();
        if (raw == null || raw.isBlank()) {
            return defaults;
        }
        try {
            Parsed parsed = parse(raw);
            if (parsed.version != ProductionWorkbenchFilterPreference.VERSION) {
                return defaults;
            }
            OrderListPeriod.Preset preset;
            try {
                preset = OrderListPeriod.Preset.valueOf(parsed.period);
            } catch (RuntimeException ex) {
                preset = defaults.periodPreset();
            }
            LocalDate from = parseDate(parsed.from);
            LocalDate to = parseDate(parsed.to);
            if (preset == OrderListPeriod.Preset.CUSTOM
                    && (from == null || to == null || from.isAfter(to))) {
                return defaults;
            }
            return new ProductionWorkbenchFilterPreference(preset, from, to);
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

    private static Parsed parse(String raw) {
        Parsed parsed = new Parsed();
        StringBuilder key = new StringBuilder();
        StringBuilder value = new StringBuilder();
        boolean inValue = false;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
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
            case "period" -> parsed.period = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
            case "from" -> parsed.from = value;
            case "to" -> parsed.to = value;
            default -> {
                // ignore unknown keys
            }
        }
    }

    private static final class Parsed {
        private int version;
        private String period = OrderListPeriod.Preset.LAST_30_DAYS.name();
        private String from = "";
        private String to = "";
    }
}
