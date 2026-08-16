package com.polymarket.markets;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Port for live CLOB book reads. The domain declares it; an internal adapter implements it,
 * so no transport type reaches this package.
 */
public interface OrderBookSource {

    Optional<OrderBookSnapshot> book(TokenId token) throws IOException;

    List<OrderBookSnapshot> books(List<TokenId> tokens) throws IOException;
}
