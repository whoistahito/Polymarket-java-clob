package com.polymarket.portfolio;

import java.util.Optional;
import lombok.NonNull;

/** Immutable open-order filter. Unset fields are never sent, so the server's defaults apply. */
public final class OpenOrderQuery {

    private final String orderId;
    private final String conditionId;
    private final String assetId;

    private OpenOrderQuery(String orderId, String conditionId, String assetId) {
        this.orderId = orderId;
        this.conditionId = conditionId;
        this.assetId = assetId;
    }

    /** The endpoint scopes itself to the authenticated account, so no filter is required. */
    public static OpenOrderQuery create() {
        return new OpenOrderQuery(null, null, null);
    }

    public OpenOrderQuery orderId(@NonNull String orderId) {
        if (orderId.isBlank()) throw new IllegalArgumentException("orderId must not be blank");
        return new OpenOrderQuery(orderId, conditionId, assetId);
    }

    public OpenOrderQuery market(@NonNull String conditionId) {
        return new OpenOrderQuery(orderId,
                QueryBoundaries.conditionIds(java.util.List.of(conditionId)).get(0), assetId);
    }

    public OpenOrderQuery asset(@NonNull String assetId) {
        if (assetId.isBlank()) throw new IllegalArgumentException("assetId must not be blank");
        return new OpenOrderQuery(orderId, conditionId, assetId);
    }

    public Optional<String> orderId() {
        return Optional.ofNullable(orderId);
    }

    public Optional<String> conditionId() {
        return Optional.ofNullable(conditionId);
    }

    public Optional<String> assetId() {
        return Optional.ofNullable(assetId);
    }
}
