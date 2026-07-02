package com.polymarket.strategy.scalp;

/**
 * A point-in-time view of both outcome tokens' top of book. Mirrors {@link
 * com.polymarket.examples.bot.LocalOrderBook} conventions so the adapter is a trivial copy: no ask
 * is signalled by {@code bestAsk == NO_ASK}, no bid by {@code bestBid == 0.0}.
 */
public record BookSnapshot(TokenQuote a, TokenQuote b) {

  public static final double NO_ASK = Double.MAX_VALUE;

  /** One side's top of book. */
  public record TokenQuote(String tokenId, double bestBid, double bestAsk, double bestAskSize) {
    public boolean hasAsk() {
      return bestAsk != NO_ASK && bestAsk > 0;
    }
  }

  public TokenQuote quoteFor(String tokenId) {
    if (a.tokenId().equals(tokenId)) return a;
    if (b.tokenId().equals(tokenId)) return b;
    throw new IllegalArgumentException("unknown tokenId: " + tokenId);
  }
}
