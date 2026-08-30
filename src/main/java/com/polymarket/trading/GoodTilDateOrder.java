package com.polymarket.trading;

import com.polymarket.markets.AssetId;
import com.polymarket.markets.Price;
import com.polymarket.markets.ShareQuantity;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import lombok.NonNull;

/**
 * A limit order that expires. Not a record: the lifetime is checked against a clock, and a public
 * canonical constructor would be a way to build one that never was.
 */
public final class GoodTilDateOrder implements OrderIntent {

    /** Official: an order dies one minute before its stated expiration. */
    public static final Duration SECURITY_THRESHOLD = Duration.ofSeconds(60);

    /** Official: the expiration must be at least three minutes ahead. */
    public static final Duration MINIMUM_LIFETIME = Duration.ofSeconds(180);

    private final AssetId asset;
    private final Side side;
    private final Price price;
    private final ShareQuantity size;
    private final Instant expiresAt;

    private GoodTilDateOrder(AssetId asset, Side side, Price price, ShareQuantity size,
            Instant expiresAt) {
        Intents.requireTradeable(asset, side, price, size);
        this.asset = asset;
        this.side = side;
        this.price = price;
        this.size = size;
        this.expiresAt = expiresAt;
    }

    /** The only construction path, so an unvalidated lifetime cannot exist. */
    public static GoodTilDateOrder expiringAt(@NonNull AssetId asset, @NonNull Side side,
            @NonNull Price price, @NonNull ShareQuantity size, @NonNull Instant expiresAt,
            @NonNull Clock clock) {
        Duration ahead = Duration.between(clock.instant(), expiresAt);
        Duration minimumEffectiveLifetime = MINIMUM_LIFETIME.minus(SECURITY_THRESHOLD);
        if (ahead.compareTo(minimumEffectiveLifetime) < 0) {
            throw new IllegalArgumentException("an effective GTD expiration must be at least "
                    + minimumEffectiveLifetime.toSeconds() + "s ahead, got "
                    + ahead.toSeconds() + "s");
        }
        return new GoodTilDateOrder(asset, side, price, size, expiresAt);
    }

    public static GoodTilDateOrder expiringAt(@NonNull AssetId asset, @NonNull Side side,
            @NonNull Price price, @NonNull ShareQuantity size, @NonNull Instant expiresAt) {
        return expiringAt(asset, side, price, size, expiresAt, Clock.systemUTC());
    }

    @Override
    public AssetId asset() {
        return asset;
    }

    @Override
    public Side side() {
        return side;
    }

    public Price price() {
        return price;
    }

    public ShareQuantity size() {
        return size;
    }

    public Instant expiresAt() {
        return expiresAt;
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

    @Override
    public boolean equals(Object o) {
        return o instanceof GoodTilDateOrder other && asset.equals(other.asset)
                && side == other.side && price.equals(other.price) && size.equals(other.size)
                && expiresAt.equals(other.expiresAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(asset, side, price, size, expiresAt);
    }

    @Override
    public String toString() {
        return "GoodTilDateOrder[asset=" + asset + ", side=" + side + ", price=" + price
                + ", size=" + size + ", expiresAt=" + expiresAt + "]";
    }
}
