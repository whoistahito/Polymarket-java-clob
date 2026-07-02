package com.polymarket.ws.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Top-level discriminated union for all WebSocket messages received from Polymarket.
 *
 * <p>Messages are distinguished by the {@code event_type} field in the JSON payload.
 * This mirrors the Rust {@code WsMessage} enum and TS socket connection message types.
 *
 * <p>Supported event types:
 * <ul>
 *   <li>{@code book} → {@link BookUpdate}</li>
 *   <li>{@code price_change} → {@link PriceChange}</li>
 *   <li>{@code tick_size_change} → {@link TickSizeChange}</li>
 *   <li>{@code last_trade_price} → {@link LastTradePrice}</li>
 *   <li>{@code best_bid_ask} → {@link BestBidAsk}</li>
 *   <li>{@code new_market} → {@link NewMarket}</li>
 *   <li>{@code market_resolved} → {@link MarketResolved}</li>
 *   <li>{@code trade} → {@link TradeMessage}</li>
 *   <li>{@code order} → {@link OrderMessage}</li>
 * </ul>
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "event_type",
    visible = true,
    defaultImpl = WsMessage.Unknown.class
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = BookUpdate.class,      name = "book"),
    @JsonSubTypes.Type(value = PriceChange.class,     name = "price_change"),
    @JsonSubTypes.Type(value = TickSizeChange.class,  name = "tick_size_change"),
    @JsonSubTypes.Type(value = LastTradePrice.class,  name = "last_trade_price"),
    @JsonSubTypes.Type(value = BestBidAsk.class,      name = "best_bid_ask"),
    @JsonSubTypes.Type(value = NewMarket.class,       name = "new_market"),
    @JsonSubTypes.Type(value = MarketResolved.class,  name = "market_resolved"),
    @JsonSubTypes.Type(value = TradeMessage.class,    name = "trade"),
    @JsonSubTypes.Type(value = OrderMessage.class,    name = "order"),
})
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class WsMessage {

    @JsonProperty("event_type")
    private String eventType;

    public String getEventType() { return eventType; }

    /** Returns {@code true} for user-channel messages (trade / order). */
    public boolean isUser() { return false; }

    /** Returns {@code true} for market-channel messages (book, price_change, etc.). */
    public boolean isMarket() { return !isUser(); }

    /** Fallback for unknown {@code event_type} values. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Unknown extends WsMessage {}
}
