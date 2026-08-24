package com.polymarket.trading;

import com.polymarket.authentication.ApiCredentials;
import lombok.NonNull;

/**
 * Submission-time attributes that ride the {@code POST /order} payload but are not part of the
 * signed struct. Only a resting order carries {@code postOnly} or a non-zero expiration, so a
 * contradictory combination is refused here rather than by the exchange.
 */
public record OrderPlacement(@NonNull ApiCredentials credentials, @NonNull OrderType orderType,
        long expirationSeconds, boolean postOnly) {

    public OrderPlacement {
        boolean resting = orderType == OrderType.GTC || orderType == OrderType.GTD;
        if (orderType == OrderType.GTD && expirationSeconds <= 0) {
            throw new IllegalArgumentException("a GTD order needs an expiration; got "
                    + expirationSeconds);
        }
        if (orderType != OrderType.GTD && expirationSeconds != 0) {
            throw new IllegalArgumentException("a " + orderType
                    + " order carries expiration 0, not " + expirationSeconds);
        }
        if (postOnly && !resting) {
            throw new IllegalArgumentException("a " + orderType
                    + " order executes immediately and cannot be post-only");
        }
    }

    /** Derives every submission attribute from the Order Intent, so none can be restated wrongly. */
    public static OrderPlacement forIntent(@NonNull ApiCredentials credentials,
            @NonNull OrderIntent intent) {
        return new OrderPlacement(credentials, intent.orderType(), intent.expirationSeconds(),
                intent.postOnly());
    }

    /** Refuses a hand-built placement that contradicts its Order Intent, before anything is sent. */
    public OrderPlacement requireConsistentWith(@NonNull OrderIntent intent) {
        if (orderType != intent.orderType() || postOnly != intent.postOnly()
                || expirationSeconds != intent.expirationSeconds()) {
            throw new IllegalArgumentException("placement " + describe(orderType, expirationSeconds,
                    postOnly) + " contradicts the Order Intent's " + describe(intent.orderType(),
                    intent.expirationSeconds(), intent.postOnly()));
        }
        return this;
    }

    private static String describe(OrderType type, long expiration, boolean postOnly) {
        return type + "/expiration=" + expiration + "/postOnly=" + postOnly;
    }

    /** Every order type except GTD, which has no meaning without an expiration. */
    public static OrderPlacement of(ApiCredentials credentials, OrderType orderType) {
        return new OrderPlacement(credentials, orderType, 0L, false);
    }

    public static OrderPlacement goodTilDate(ApiCredentials credentials, long epochSeconds) {
        return new OrderPlacement(credentials, OrderType.GTD, epochSeconds, false);
    }

    public OrderPlacement expiringAt(long epochSeconds) {
        return new OrderPlacement(credentials, orderType, epochSeconds, postOnly);
    }

    public OrderPlacement asPostOnly() {
        return new OrderPlacement(credentials, orderType, expirationSeconds, true);
    }
}
