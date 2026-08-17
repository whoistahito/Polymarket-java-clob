package com.polymarket.builders;

import java.util.Objects;
import java.util.Optional;

/** Optional filter for a builder trades read; every field narrows, none is required. */
public final class BuilderTradeQuery {

    private final String id;
    private final String market;
    private final String assetId;

    private BuilderTradeQuery(String id, String market, String assetId) {
        this.id = id;
        this.market = market;
        this.assetId = assetId;
    }

    public static BuilderTradeQuery create() {
        return new BuilderTradeQuery(null, null, null);
    }

    public BuilderTradeQuery id(String id) {
        return new BuilderTradeQuery(Objects.requireNonNull(id, "id"), market, assetId);
    }

    public BuilderTradeQuery market(String market) {
        return new BuilderTradeQuery(id, Objects.requireNonNull(market, "market"), assetId);
    }

    public BuilderTradeQuery assetId(String assetId) {
        return new BuilderTradeQuery(id, market, Objects.requireNonNull(assetId, "assetId"));
    }

    public Optional<String> id() {
        return Optional.ofNullable(id);
    }

    public Optional<String> market() {
        return Optional.ofNullable(market);
    }

    public Optional<String> assetId() {
        return Optional.ofNullable(assetId);
    }
}
