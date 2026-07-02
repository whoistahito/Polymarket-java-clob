package com.polymarket.strategy.scalp;

import com.polymarket.ws.model.TradeMessage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Turns the user-channel {@link TradeMessage} stream into a net share position per token. A single
 * trade id can appear several times as it advances MATCHED -&gt; MINED -&gt; CONFIRMED, so each id is
 * applied exactly once. BUY fills add, SELL fills subtract.
 *
 * <p>ponytail: counts a fill on first sight with a matched/mined/confirmed status. FAILED/RETRYING
 * trades never move shares, so they're ignored; a matched trade that later fails on-chain is a rare
 * edge the strategy's DONE re-flatten still cleans up, so we don't wait for CONFIRMED (too slow for
 * a 60s scalp).
 */
public class ScalpFillTracker {

  private static final Set<String> FILLED_STATUSES = Set.of("MATCHED", "MINED", "CONFIRMED");

  private final Set<String> appliedTradeIds = new HashSet<>();
  private final Map<String, Double> netByToken = new HashMap<>();

  /**
   * Apply a trade and return the new net position for its token, or {@code null} if the trade was a
   * duplicate, non-fill, or malformed (nothing changed).
   */
  public synchronized Double apply(TradeMessage trade) {
    if (trade == null || trade.getId() == null || trade.getAssetId() == null) {
      return null;
    }
    String status = trade.getStatus() == null ? "" : trade.getStatus().toUpperCase();
    if (!FILLED_STATUSES.contains(status)) {
      return null;
    }
    if (!appliedTradeIds.add(trade.getId())) {
      return null; // already counted this fill
    }

    double size;
    try {
      size = Double.parseDouble(trade.getSize());
    } catch (NumberFormatException | NullPointerException e) {
      appliedTradeIds.remove(trade.getId()); // wasn't really applied
      return null;
    }

    double signed = "SELL".equalsIgnoreCase(trade.getSide()) ? -size : size;
    double net = Math.max(0, netByToken.getOrDefault(trade.getAssetId(), 0.0) + signed);
    netByToken.put(trade.getAssetId(), net);
    return net;
  }
}
