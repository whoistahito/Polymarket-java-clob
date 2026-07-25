package com.polymarket.model.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/**
 * One current position returned by {@code GET https://data-api.polymarket.com/positions}
 * (Ticket 025).
 *
 * <p><b>This is an absolute snapshot, not a delta.</b> The size reported here is the wallet's whole
 * current holding of {@link #asset} at the moment of the read, so a later snapshot legitimately
 * reports LESS after a sell. The SDK deliberately imposes no monotonic semantics: it reports what
 * the API said. Deciding whether a smaller figure is a real reduction or a stale read is the
 * caller's job, and it needs the unmodified number to do it.
 *
 * <p>Every numeric field is a {@link BigDecimal} so sizes and prices survive exactly; optional
 * fields stay {@code null} when the response omits them rather than defaulting to zero, which would
 * be indistinguishable from a real zero.
 */
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(builder = DataPosition.DataPositionBuilder.class)
public class DataPosition {

    /** The holder's proxy wallet address (0x-prefixed). */
    String proxyWallet;

    /** The outcome token ID held. This is what an order routes against. */
    String asset;

    /** The market's condition ID (0x-prefixed 64-hex string). */
    String conditionId;

    /** Current holding in shares — absolute, and may decrease between snapshots. */
    BigDecimal size;

    /** Average entry price across the holding. */
    BigDecimal avgPrice;

    /** Cost basis of the position. */
    BigDecimal initialValue;

    /** Mark-to-market value of the position. */
    BigDecimal currentValue;

    /** Unrealized profit and loss in USDC. */
    BigDecimal cashPnl;

    /** Unrealized profit and loss as a percentage. */
    BigDecimal percentPnl;

    /** Lifetime shares bought. */
    BigDecimal totalBought;

    /** Realized profit and loss in USDC. */
    BigDecimal realizedPnl;

    /** Realized profit and loss as a percentage. */
    BigDecimal percentRealizedPnl;

    /** Current market price for the held token. */
    BigDecimal curPrice;

    /** Whether the position can be redeemed (the market resolved). */
    Boolean redeemable;

    /** Whether the position can be merged against the opposite outcome. */
    Boolean mergeable;

    /** Market title / question. */
    String title;

    /** Market URL slug. */
    String slug;

    /** Market icon URL. */
    String icon;

    /** Parent event URL slug. */
    String eventSlug;

    /** Outcome label for the held token, e.g. {@code "Up"}. Cosmetic — route on {@link #asset}. */
    String outcome;

    /** Index of the held outcome within the market (0 or 1 for a binary market). */
    int outcomeIndex;

    /** Outcome label of the other side. */
    String oppositeOutcome;

    /** Token ID of the other side, useful when both outcomes are held. */
    String oppositeAsset;

    /** Market end date as an ISO-8601 string, preserved verbatim. */
    String endDate;

    /** Whether the market is a neg-risk market. */
    Boolean negativeRisk;

    @JsonPOJOBuilder(withPrefix = "")
    public static final class DataPositionBuilder {}
}
