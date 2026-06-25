package com.tradepulse.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utility methods for monetary and decimal values.
 * <p>
 * All monetary values use DECIMAL(18,8) precision to match the database schema.
 * Never use double or float for monetary calculations.
 */
public final class DecimalUtils {

    public static final int MONETARY_SCALE = 8;
    public static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;

    private DecimalUtils() {}

    /** Rounds a BigDecimal to the standard monetary scale (8 decimal places). */
    public static BigDecimal round(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO;
        return value.setScale(MONETARY_SCALE, DEFAULT_ROUNDING);
    }

    /** Rounds to a custom scale. */
    public static BigDecimal round(BigDecimal value, int scale) {
        if (value == null) return BigDecimal.ZERO;
        return value.setScale(scale, DEFAULT_ROUNDING);
    }

    /** Returns true if value is positive (> 0). */
    public static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    /** Returns true if value is zero or positive (>= 0). */
    public static boolean isNonNegative(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) >= 0;
    }

    /** Safe subtraction — returns ZERO if result would be negative. */
    public static BigDecimal subtractSafe(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return BigDecimal.ZERO;
        BigDecimal result = a.subtract(b);
        return result.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : result;
    }

    /** Parses a string to BigDecimal; returns ZERO on null/blank/parse error. */
    public static BigDecimal parseOrZero(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
