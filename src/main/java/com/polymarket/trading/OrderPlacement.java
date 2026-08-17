package com.polymarket.trading;

import com.polymarket.authentication.ApiCredentials;
import java.util.Objects;

/**
 * Submission-time attributes that ride the {@code POST /order} payload but are not part of the
 * signed struct. {@code postOnly} only applies to GTC/GTD; {@code expirationSeconds} is 0 outside GTD.
 */
public record OrderPlacement(
        ApiCredentials credentials, OrderType orderType, long expirationSeconds, boolean postOnly) {

    public OrderPlacement {
        Objects.requireNonNull(credentials, "credentials");
        Objects.requireNonNull(orderType, "orderType");
    }

    public static OrderPlacement of(ApiCredentials credentials, OrderType orderType) {
        return new OrderPlacement(credentials, orderType, 0L, false);
    }

    public OrderPlacement expiringAt(long epochSeconds) {
        return new OrderPlacement(credentials, orderType, epochSeconds, postOnly);
    }

    public OrderPlacement asPostOnly() {
        return new OrderPlacement(credentials, orderType, expirationSeconds, true);
    }
}
