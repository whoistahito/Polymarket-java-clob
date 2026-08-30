package com.polymarket.rfq;

import java.io.IOException;

/**
 * Port for discovering Combo-eligible markets and their leg Position IDs. The domain declares
 * it; an internal adapter implements it, so no transport type reaches this package.
 */
public interface ComboMarketCatalog {

    ComboMarketPage comboMarkets(ComboMarketQuery query) throws IOException;
}
