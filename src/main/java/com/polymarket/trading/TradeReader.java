package com.polymarket.trading;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.SigningIdentity;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import lombok.NonNull;

/** Reads settled trades by ID. A missing ID simply produces no record — not an error. */
public interface TradeReader {

    /**
     * The identity separates the L2 Account Signer from the Trading Wallet the filter names.
     * {@code deadline} bounds the read itself: one trade id can span several pages, and a caller
     * that has run out of time is owed what was seen so far, not a walk that overshoots it.
     */
    List<SettledTrade> byIds(@NonNull ApiCredentials credentials, @NonNull SigningIdentity identity,
            @NonNull List<String> tradeIds, @NonNull Instant deadline) throws IOException;
}
