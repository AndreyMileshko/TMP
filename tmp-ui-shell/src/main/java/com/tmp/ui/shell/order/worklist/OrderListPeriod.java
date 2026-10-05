package com.tmp.ui.shell.order.worklist;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Created-at period for Orders / Production list filters. Bounded presets resolve against
 * {@link Clock}. {@link Preset#ALL_PERIOD} uses an open (unbounded) range.
 */
public final class OrderListPeriod {

    public enum Preset {
        TODAY,
        LAST_7_DAYS,
        LAST_30_DAYS,
        CURRENT_MONTH,
        ALL_PERIOD,
        CUSTOM
    }

    /**
     * Inclusive lower / exclusive upper created-at bounds. Both null means unbounded (all period).
     * When present, both must be non-null and {@code fromInclusive < toExclusive}.
     */
    public record Range(Instant fromInclusive, Instant toExclusive) {
        public Range {
            if ((fromInclusive == null) != (toExclusive == null)) {
                throw new IllegalArgumentException(
                        "fromInclusive and toExclusive must both be present or both absent");
            }
            if (fromInclusive != null && !fromInclusive.isBefore(toExclusive)) {
                throw new IllegalArgumentException(
                        "fromInclusive must be before toExclusive: "
                                + fromInclusive
                                + " / "
                                + toExclusive);
            }
        }

        public boolean unbounded() {
            return fromInclusive == null;
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
            case CURRENT_MONTH ->
                    ofDays(today.withDayOfMonth(1), today.withDayOfMonth(1).plusMonths(1), zoneId);
            case ALL_PERIOD -> new Range(null, null);
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
