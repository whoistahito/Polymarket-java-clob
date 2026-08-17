package com.polymarket.trading;

import com.polymarket.authentication.ApiCredentials;
import java.io.IOException;
import java.util.List;

/** Reads settled trades by ID. A missing ID simply produces no record — not an error. */
public interface TradeReader {

    List<SettledTrade> byIds(ApiCredentials credentials, String address, List<String> tradeIds)
            throws IOException;
}
