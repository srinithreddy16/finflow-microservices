package com.finflow.common.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * UTC-centric formatting helpers for API and log surfaces.
 */
public final class DateTimeUtils {

    public static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    public static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private DateTimeUtils() {}

    public static Instant now() {
        return Instant.now();
    }

    public static String format(Instant instant) {
        return ISO_FORMATTER.format(instant);
    }

    public static String formatDate(Instant instant) {
        return DATE_FORMATTER.format(instant);
    }
}
