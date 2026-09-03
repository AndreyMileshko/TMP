package com.tmp.ui.shell.order.worklist;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Mandatory created-at period for the Orders list. Dynamic presets are resolved against {@link Clock}.
 */
public final class OrderListPeriod {

    public enum Preset {
        TODAY,
        LAST_7_DAYS,
        LAST_30_DAYS,
        CURRENT_MONTH,
        CUSTOM
    }

    public record Range(Instant fromInclusive, Instant toExclusive) {
        public Range {
            Objects.requireNonNull(fromInclusive, "fromInclusive");
            Objects.requireNonNull(toExclusive, "toExclusive");
            if (!fromInclusive.isBefore(toExclusive)) {
                throw new IllegalArgumentException(
                        "fromInclusive must be before toExclusive: "
                                + fromInclusive
                                + " / "
                                + toExclusive);
            }
        }
    }

    private OrderListPeriod() {}

    public static Range resolve(
            Preset preset, ZoneId zoneId, Clock clock, LocalDate customFrom, LocalDate customTo) {
        Objects.requireNonNull(preset, "preset");
        Objects.requireNonNull(zoneId, "zoneId");
        Objects.requireNonNull(clock, "clock");
        LocalDate today = LocalDate.now(clock.withZone(zoneId));
        return switch (preset) {
            case TODAY -> ofDays(today, today.plusDays(1), zoneId);
            case LAST_7_DAYS -> ofDays(today.minusDays(6), today.plusDays(1), zoneId);
            case LAST_30_DAYS -> ofDays(today.minusDays(29), today.plusDays(1), zoneId);
            case CURRENT_MONTH -> ofDays(today.withDayOfMonth(1), today.withDayOfMonth(1).plusMonths(1), zoneId);
            case CUSTOM -> resolveCustom(customFrom, customTo, zoneId);
        };
    }

    private static Range resolveCustom(LocalDate customFrom, LocalDate customTo, ZoneId zoneId) {
        Objects.requireNonNull(customFrom, "customFrom");
        Objects.requireNonNull(customTo, "customTo");
        if (customFrom.isAfter(customTo)) {
            throw new IllegalArgumentException("customFrom must not be after customTo");
        }
        return ofDays(customFrom, customTo.plusDays(1), zoneId);
    }

    private static Range ofDays(LocalDate fromInclusive, LocalDate toExclusive, ZoneId zoneId) {
        return new Range(
                fromInclusive.atStartOfDay(zoneId).toInstant(),
                toExclusive.atStartOfDay(zoneId).toInstant());
    }
}
