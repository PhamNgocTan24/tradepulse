package com.tradepulse.common.util;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Shared validation helpers — pure functions with no side effects.
 */
public final class ValidationUtils {

    private ValidationUtils() {}

    /** Returns true if the string is non-null and non-blank. */
    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** Parses a UUID string; returns null on invalid format. */
    public static UUID parseUuidOrNull(String value) {
        if (!hasText(value)) return null;
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Returns true if the trading symbol matches the expected format (e.g. BTCUSDT). */
    public static boolean isValidSymbol(String symbol) {
        return hasText(symbol) && symbol.matches("[A-Z]{3,10}");
    }

    /** Returns true if quantity is positive and within an acceptable scale. */
    public static boolean isValidQuantity(BigDecimal quantity) {
        return DecimalUtils.isPositive(quantity);
    }

    /** Returns true if price is positive. */
    public static boolean isValidPrice(BigDecimal price) {
        return DecimalUtils.isPositive(price);
    }
}
