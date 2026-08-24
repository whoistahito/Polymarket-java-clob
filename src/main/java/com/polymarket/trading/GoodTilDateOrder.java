package com.polymarket.trading;

import com.polymarket.markets.AssetId;
import com.polymarket.markets.Price;
import com.polymarket.markets.ShareQuantity;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A limit order that expires. Expiration is checked locally against the official minimum so a
 * too-soon order fails here rather than at the exchange.
 */
public record GoodTilDateOrder(
        AssetId asset, Side side, Price price, ShareQuantity size, Instant expiresAt)
        implements OrderIntent {

    /** Official: an order dies one minute before its stated expiration. */
    public static final Duration SECURITY_THRESHOLD = Duration.ofSeconds(60);

    /** Official: the expiration must be at least three minutes ahead. */
    public static final Duration MINIMUM_LIFETIME = Duration.ofSeconds(180);

    public GoodTilDateOrder {
        Intents.requireTradeable(asset, side, price, size);
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public static GoodTilDateOrder expiringAt(AssetId asset, Side side, Price price,
            ShareQuantity size, Instant expiresAt, Clock clock) {
        Objects.requireNonNull(expiresAt, "expiresAt");
        Duration ahead = Duration.between(Objects.requireNonNull(clock, "clock").instant(), expiresAt);
        if (ahead.compareTo(MINIMUM_LIFETIME) < 0) {
            throw new IllegalArgumentException("a GTD expiration must be at least "
                    + MINIMUM_LIFETIME.toSeconds() + "s ahead, got " + ahead.toSeconds() + "s");
        }
        return new GoodTilDateOrder(asset, side, price, size, expiresAt);
    }

    @Override
    public OrderType orderType() {
        return OrderType.GTD;
    }

    @Override
    public boolean postOnly() {
        return false;
    }

    /**
     * The value to put on the wire: the exchange subtracts its one-minute threshold, so the
     * caller's intended expiry has to be shifted forward to survive until then.
     */
    @Override
    public long expirationSeconds() {
        return expiresAt.plus(SECURITY_THRESHOLD).getEpochSecond();
    }
}
