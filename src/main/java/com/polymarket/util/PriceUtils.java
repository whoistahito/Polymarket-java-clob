package com.polymarket.util;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polymarket.model.OrderBookSummary;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for price calculations and tick rounding.
 */
public class PriceUtils {

    // High precision math context (18 decimal places, matching blockchain precision)
    public static final MathContext MATH_CONTEXT = new MathContext(18, RoundingMode.HALF_UP);

    // Standard precision for display (4 decimal places)
    private static final int DISPLAY_SCALE = 4;

    // Common constants
    public static final BigDecimal ZERO = BigDecimal.ZERO;
    public static final BigDecimal ONE = BigDecimal.ONE;
    public static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");

    /**
     * Round a price to the market's tick size.
     *
     * @param price The price to round
     * @param tickSize The market's tick size
     * @param mode Rounding mode: "up", "down", or "nearest"
     * @return Rounded price
     */
    public static BigDecimal tickRound(BigDecimal price, BigDecimal tickSize, String mode) {
        if (price == null || tickSize == null || tickSize.compareTo(ZERO) <= 0) {
            return price;
        }

        // Divide price by tick size to get number of ticks
        BigDecimal ticks = price.divide(tickSize, MATH_CONTEXT);

        BigDecimal roundedTicks;
        switch (mode.toLowerCase()) {
            case "up":
                // Round up to next tick (CEILING)
                roundedTicks = ticks.setScale(0, RoundingMode.CEILING);
                break;
            case "down":
                // Round down to previous tick (FLOOR)
                roundedTicks = ticks.setScale(0, RoundingMode.FLOOR);
                break;
            case "nearest":
            default:
                // Round to nearest tick (HALF_UP)
                roundedTicks = ticks.setScale(0, RoundingMode.HALF_UP);
                break;
        }

        // Multiply back by tick size
        return roundedTicks.multiply(tickSize);
    }

    /**
     * Convert fee rate from basis points to decimal.
     * Example: 100 bps -> 0.01 (1%)
     */
    public static BigDecimal feeRateFromBps(int bps) {
        return new BigDecimal(bps).divide(TEN_THOUSAND, MATH_CONTEXT);
    }

    /**
     * Safe conversion from string to BigDecimal.
     * Returns null if conversion fails.
     */
    public static BigDecimal safeBigDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Safe conversion with default value.
     */
    public static BigDecimal safeBigDecimal(String value, BigDecimal defaultValue) {
        BigDecimal result = safeBigDecimal(value);
        return result != null ? result : defaultValue;
    }

    /**
     * Format BigDecimal for display (4 decimal places).
     */
    public static String formatPrice(BigDecimal price) {
        if (price == null) {
            return "null";
        }
        return price.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Format BigDecimal as money string with $ prefix.
     */
    public static String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "$null";
        }
        return "$" + formatPrice(amount);
    }

    /**
     * Format BigDecimal as percentage (2 decimal places).
     */
    public static String formatPercentage(BigDecimal percentage) {
        if (percentage == null) {
            return "null%";
        }
        return percentage.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    /**
     * Compare two BigDecimal values with tolerance.
     * Returns true if values are equal within tolerance.
     */
    public static boolean equalWithinTolerance(BigDecimal a, BigDecimal b, BigDecimal tolerance) {
        if (a == null || b == null) {
            return a == b;
        }
        BigDecimal diff = a.subtract(b).abs();
        return diff.compareTo(tolerance) <= 0;
    }

    /**
     * Clamp value between min and max.
     */
    public static BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value.compareTo(min) < 0) {
            return min;
        }
        if (value.compareTo(max) > 0) {
            return max;
        }
        return value;
    }

    /**
     * Check if price is valid (between 0 and 1).
     */
    public static boolean isValidPrice(BigDecimal price) {
        if (price == null) {
            return false;
        }
        return price.compareTo(ZERO) >= 0 && price.compareTo(ONE) <= 0;
    }

    /**
     * Calculate minimum required amount for a trade.
     */
    public static BigDecimal calculateRequiredAmount(BigDecimal price, BigDecimal size) {
        return price.multiply(size);
    }

    /**
     * Round to specified decimal places.
     */
    public static BigDecimal round(BigDecimal value, int scale, RoundingMode mode) {
        if (value == null) {
            return null;
        }
        return value.setScale(scale, mode);
    }

    /**
     * Max of two BigDecimal values.
     */
    public static BigDecimal max(BigDecimal a, BigDecimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.compareTo(b) >= 0 ? a : b;
    }

    /**
     * Min of two BigDecimal values.
     */
    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.compareTo(b) <= 0 ? a : b;
    }

    // ---------------------------------------------------------------------------
    // Methods added in Phase 3 — mirrors clob-client/src/utilities.ts
    // ---------------------------------------------------------------------------

    /**
     * Check whether {@code price} is a valid order price for the given tick size.
     *
     * <p>A price is valid when {@code price >= tickSize} and {@code price <= 1 - tickSize}.
     * Mirrors the TypeScript {@code priceValid(price, tickSize)} helper.
     *
     * @param price    the proposed order price (0 < price < 1)
     * @param tickSize the market tick size string, e.g. {@code "0.01"}
     * @return {@code true} if the price falls within the allowed range
     */
    public static boolean priceValid(BigDecimal price, String tickSize) {
        if (price == null || tickSize == null) return false;
        BigDecimal tick = new BigDecimal(tickSize);
        BigDecimal upper = ONE.subtract(tick);
        return price.compareTo(tick) >= 0 && price.compareTo(upper) <= 0;
    }

    /**
     * Return {@code true} when tick-size {@code a} is smaller than tick-size {@code b}.
     *
     * <p>Mirrors the TypeScript {@code isTickSizeSmaller(a, b)} helper.
     *
     * @param a first tick-size string, e.g. {@code "0.001"}
     * @param b second tick-size string, e.g. {@code "0.01"}
     * @return {@code true} if {@code parseFloat(a) < parseFloat(b)}
     */
    public static boolean isTickSizeSmaller(String a, String b) {
        if (a == null || b == null) return false;
        return new BigDecimal(a).compareTo(new BigDecimal(b)) < 0;
    }

    /**
     * Returns the number of decimal places in the given tick size string.
     *
     * <p>Mirrors the TypeScript {@code decimalPlaces(num)} helper in
     * {@code clob-client/src/utilities.ts}, adapted for Java where tick sizes
     * are always passed as strings (e.g., {@code "0.01"}).
     *
     * <pre>
     * decimalPlaces("0.1")    == 1
     * decimalPlaces("0.01")   == 2
     * decimalPlaces("0.001")  == 3
     * decimalPlaces("0.0001") == 4
     * </pre>
     *
     * @param tickSize tick size string, e.g. {@code "0.01"}
     * @return number of decimal places; 0 if no decimal point is found
     */
    public static int decimalPlaces(String tickSize) {
        if (tickSize == null || tickSize.isEmpty()) {
            return 0;
        }
        int dotIndex = tickSize.indexOf('.');
        if (dotIndex < 0) {
            return 0;
        }
        return tickSize.length() - dotIndex - 1;
    }

    /**
     * Constructs a {@link com.polymarket.model.PostOrderPayload} from a signed order and
     * posting metadata, enforcing that {@code postOnly} is only valid for GTC/GTD orders.
     *
     * <p>Mirrors the TypeScript {@code orderToJson(order, owner, orderType, deferExec, postOnly)}
     * helper in {@code clob-client/src/utilities.ts}.
     *
     * @param order      the signed order
     * @param owner      the API key (owner identifier) string
     * @param orderType  the order type
     * @param deferExec  whether to defer execution
     * @param postOnly   {@code true} to request post-only; must be {@code null}/omitted or
     *                   {@code false} for FOK and FAK orders
     * @return a ready-to-post {@link com.polymarket.model.PostOrderPayload}
     * @throws IllegalArgumentException if {@code postOnly=true} and orderType is FOK or FAK
     */
    public static com.polymarket.model.PostOrderPayload orderToJson(
            com.polymarket.model.SignedOrder order,
            String owner,
            com.polymarket.model.OrderType orderType,
            boolean deferExec,
            Boolean postOnly) {
        if (Boolean.TRUE.equals(postOnly)
                && (orderType == com.polymarket.model.OrderType.FOK
                        || orderType == com.polymarket.model.OrderType.FAK)) {
            throw new IllegalArgumentException(
                    "postOnly is only supported for GTC and GTD orders");
        }
        return com.polymarket.model.PostOrderPayload.builder()
                .order(order)
                .owner(owner)
                .orderType(orderType)
                .deferExec(deferExec)
                .postOnly(postOnly)
                .build();
    }

    private static final ObjectMapper HASH_MAPPER = new ObjectMapper();

    /**
     * Compute the SHA-1 hash for an order-book snapshot.
     *
     * <p>Mirrors the TypeScript {@code generateOrderBookSummaryHash(orderbook)} helper.
     * The algorithm:
     * <ol>
     *   <li>Serialize the book to JSON with {@code "hash":""}</li>
     *   <li>Compute SHA-1 of the UTF-8 bytes</li>
     *   <li>Return the lower-case hex digest string</li>
     * </ol>
     *
     * @param orderbook the order-book snapshot
     * @return hex SHA-1 digest
     */
    public static String generateOrderBookSummaryHash(OrderBookSummary orderbook) {
        try {
            // Serialize the book to a JSON object node so we can set hash=""
            ObjectNode node = HASH_MAPPER.valueToTree(orderbook);
            node.put("hash", "");
            String json = HASH_MAPPER.writeValueAsString(node);

            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest(json.getBytes(StandardCharsets.UTF_8));

            // Convert bytes to hex string
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 not available", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute order book hash", e);
        }
    }
}
