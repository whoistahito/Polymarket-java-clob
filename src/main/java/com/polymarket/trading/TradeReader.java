package com.polymarket.trading;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.SigningIdentity;
import java.io.IOException;
import java.util.List;
import lombok.NonNull;

/** Reads settled trades by ID. A missing ID simply produces no record — not an error. */
public interface TradeReader {

    /** The identity separates the L2 Account Signer from the Trading Wallet the filter names. */
    List<SettledTrade> byIds(@NonNull ApiCredentials credentials, @NonNull SigningIdentity identity,
            @NonNull List<String> tradeIds) throws IOException;
}
