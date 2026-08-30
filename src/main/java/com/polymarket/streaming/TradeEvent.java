package com.polymarket.streaming;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/** User trade-execution message ({@code event_type: "trade"}), authenticated-channel only. */
public record TradeEvent(@NonNull String id, @NonNull String market, @NonNull String assetId,
        @NonNull String side, @NonNull BigDecimal size, @NonNull BigDecimal price,
        @NonNull String status, @NonNull String type, @NonNull Optional<String> lastUpdate,
        @NonNull Optional<String> matchTime, @NonNull Optional<String> timestamp,
        @NonNull Optional<String> outcome, @NonNull Optional<String> owner,
        @NonNull Optional<String> tradeOwner, @NonNull Optional<String> makerAddress,
        @NonNull Optional<String> takerOrderId, @NonNull List<MakerOrder> makerOrders,
        @NonNull Optional<String> feeRateBps, @NonNull Optional<String> transactionHash,
        @NonNull Optional<Integer> bucketIndex, @NonNull Optional<String> traderSide) {

    public TradeEvent {
        makerOrders = List.copyOf(makerOrders);
    }
}
