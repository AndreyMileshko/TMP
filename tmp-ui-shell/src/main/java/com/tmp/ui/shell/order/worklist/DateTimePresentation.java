package com.tmp.ui.shell.order.worklist;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Application timezone presentation for Orders dates. Uses the JVM default zone.
 */
public final class DateTimePresentation {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String ABSENT = "—";

    private DateTimePresentation() {}

    public static String format(Instant instant) {
        return format(instant, ZoneId.systemDefault());
    }

    public static String format(Instant instant, ZoneId zoneId) {
        Objects.requireNonNull(instant, "instant");
        Objects.requireNonNull(zoneId, "zoneId");
        return FORMAT.withZone(zoneId).format(instant);
    }

    /**
     * Formats a calendar date as {@code dd.MM.yyyy}, or {@code —} when {@code date} is null.
     */
    public static String formatDate(LocalDate date) {
        if (date == null) {
            return ABSENT;
        }
        return DATE_ONLY.format(date);
    }

    public static String customerDisplay(String customerName) {
        if (customerName == null || customerName.isBlank()) {
            return ABSENT;
        }
        return customerName;
    }

    public static LocalDate toLocalDate(Instant instant, ZoneId zoneId) {
        Objects.requireNonNull(instant, "instant");
        Objects.requireNonNull(zoneId, "zoneId");
        return instant.atZone(zoneId).toLocalDate();
    }
}
