package com.polymarket.builders;

import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.NonNull;

/**
 * Filter for a builder trades read. The Builder code is required — the CLOB rejects a read
 * without one and never defaults to the caller's own code; every other field only narrows.
 */
public final class BuilderTradeQuery {

    // Documented shape of a Builder code: ^0x[a-fA-F0-9]{64}$ (GET /builder/trades).
    private static final Pattern BUILDER_CODE = Pattern.compile("^0x[a-fA-F0-9]{64}$");

    private final String builderCode;
    private final String id;
    private final String market;
    private final String assetId;
    private final Instant before;
    private final Instant after;

    private BuilderTradeQuery(String builderCode, String id, String market, String assetId,
            Instant before, Instant after) {
        this.builderCode = builderCode;
        this.id = id;
        this.market = market;
        this.assetId = assetId;
        this.before = before;
        this.after = after;
    }

    public static BuilderTradeQuery forBuilder(@NonNull String builderCode) {
        if (!BUILDER_CODE.matcher(builderCode).matches()) {
            throw new IllegalArgumentException(
                    "builderCode must be 32 hex bytes, as 0x + 64 hex digits");
        }
        return new BuilderTradeQuery(builderCode, null, null, null, null, null);
    }

    public BuilderTradeQuery id(@NonNull String id) {
        return new BuilderTradeQuery(builderCode, id, market, assetId, before, after);
    }

    public BuilderTradeQuery market(@NonNull String market) {
        return new BuilderTradeQuery(builderCode, id, market, assetId, before, after);
    }

    public BuilderTradeQuery assetId(@NonNull String assetId) {
        return new BuilderTradeQuery(builderCode, id, market, assetId, before, after);
    }

    /** Upper bound of the match-time window; it travels as unix seconds, not an ISO instant. */
    public BuilderTradeQuery before(@NonNull Instant before) {
        return new BuilderTradeQuery(builderCode, id, market, assetId, before, after);
    }

    /** Lower bound of the match-time window; it travels as unix seconds, not an ISO instant. */
    public BuilderTradeQuery after(@NonNull Instant after) {
        return new BuilderTradeQuery(builderCode, id, market, assetId, before, after);
    }

    public String builderCode() {
        return builderCode;
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

    public Optional<Instant> before() {
        return Optional.ofNullable(before);
    }

    public Optional<Instant> after() {
        return Optional.ofNullable(after);
    }
}
