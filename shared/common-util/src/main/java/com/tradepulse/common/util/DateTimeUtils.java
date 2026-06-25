package com.tradepulse.common.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Shared date/time helpers.
 * All timestamps are stored and transmitted in UTC.
 */
public final class DateTimeUtils {

    public static final DateTimeFormatter ISO_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private DateTimeUtils() {}

    /** Returns the current UTC instant. */
    public static Instant nowUtc() {
        return Instant.now();
    }

    /** Formats an Instant to ISO-8601 UTC string. */
    public static String formatUtc(Instant instant) {
        if (instant == null) return null;
        return ISO_UTC.format(instant);
    }

    /** Converts an epoch-millis long to Instant. */
    public static Instant fromEpochMilli(long epochMilli) {
        return Instant.ofEpochMilli(epochMilli);
    }

    /** Returns a ZonedDateTime from an Instant in UTC. */
    public static ZonedDateTime toUtcZoned(Instant instant) {
        if (instant == null) return null;
        return instant.atZone(ZoneOffset.UTC);
    }
}
