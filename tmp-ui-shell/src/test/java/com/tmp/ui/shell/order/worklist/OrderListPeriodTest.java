package com.tmp.ui.shell.order.worklist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class OrderListPeriodTest {

    private static final ZoneId ZONE = ZoneOffset.UTC;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-03T10:15:00Z"), ZONE);

    @Test
    void lastThirtyDaysIsDynamicInclusiveWindow() {
        OrderListPeriod.Range range =
                OrderListPeriod.resolve(OrderListPeriod.Preset.LAST_30_DAYS, ZONE, CLOCK, null, null);
        assertEquals(Instant.parse("2026-08-05T00:00:00Z"), range.fromInclusive());
        assertEquals(Instant.parse("2026-09-04T00:00:00Z"), range.toExclusive());
    }

    @Test
    void todayUsesStartOfDayExclusiveNextDay() {
        OrderListPeriod.Range range =
                OrderListPeriod.resolve(OrderListPeriod.Preset.TODAY, ZONE, CLOCK, null, null);
        assertEquals(Instant.parse("2026-09-03T00:00:00Z"), range.fromInclusive());
        assertEquals(Instant.parse("2026-09-04T00:00:00Z"), range.toExclusive());
    }

    @Test
    void lastSevenDaysAndCurrentMonthUseClock() {
        OrderListPeriod.Range week =
                OrderListPeriod.resolve(OrderListPeriod.Preset.LAST_7_DAYS, ZONE, CLOCK, null, null);
        assertEquals(Instant.parse("2026-08-28T00:00:00Z"), week.fromInclusive());
        OrderListPeriod.Range month =
                OrderListPeriod.resolve(OrderListPeriod.Preset.CURRENT_MONTH, ZONE, CLOCK, null, null);
        assertEquals(Instant.parse("2026-09-01T00:00:00Z"), month.fromInclusive());
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), month.toExclusive());
    }

    @Test
    void customUsesExactDatesAndRejectsInvertedRange() {
        OrderListPeriod.Range range =
                OrderListPeriod.resolve(
                        OrderListPeriod.Preset.CUSTOM,
                        ZONE,
                        CLOCK,
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 8, 10));
        assertEquals(Instant.parse("2026-08-01T00:00:00Z"), range.fromInclusive());
        assertEquals(Instant.parse("2026-08-11T00:00:00Z"), range.toExclusive());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        OrderListPeriod.resolve(
                                OrderListPeriod.Preset.CUSTOM,
                                ZONE,
                                CLOCK,
                                LocalDate.of(2026, 8, 10),
                                LocalDate.of(2026, 8, 1)));
    }
}
