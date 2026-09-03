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

    private DateTimePresentation() {}

    public static String format(Instant instant) {
        return format(instant, ZoneId.systemDefault());
    }

    public static String format(Instant instant, ZoneId zoneId) {
        Objects.requireNonNull(instant, "instant");
        Objects.requireNonNull(zoneId, "zoneId");
        return FORMAT.withZone(zoneId).format(instant);
    }

    public static String customerDisplay(String customerName) {
        if (customerName == null || customerName.isBlank()) {
            return "—";
        }
        return customerName;
    }

    public static LocalDate toLocalDate(Instant instant, ZoneId zoneId) {
        Objects.requireNonNull(instant, "instant");
        Objects.requireNonNull(zoneId, "zoneId");
        return instant.atZone(zoneId).toLocalDate();
    }
}
