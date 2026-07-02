package com.polymarket.examples.bot;

import com.polymarket.ws.model.BookUpdate;
import com.polymarket.ws.model.OrderBookLevel;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * An in-memory representation of the CLOB (Central Limit Order Book) for a single token. It
 * processes WebSocket BookUpdate messages to apply deltas and maintains $O(1)$ read access to the
 * best bid and best ask for ultra-fast arbitrage evaluation.
 */
public class LocalOrderBook {

  private final String tokenId;

  // Bids: highest price first
  private final TreeMap<Double, Double> bids = new TreeMap<>(Collections.reverseOrder());

  // Asks: lowest price first
  private final TreeMap<Double, Double> asks = new TreeMap<>();

  // Fast cache for $O(1) access
  private double bestBidPrice = 0.0;
  private double bestBidSize = 0.0;

  private double bestAskPrice = Double.MAX_VALUE;
  private double bestAskSize = 0.0;

  public LocalOrderBook(String tokenId) {
    this.tokenId = tokenId;
  }

  /**
   * Applies a snapshot or delta update from the WebSocket feed.
   *
   * @param update the incoming BookUpdate message
   */
  public void processUpdate(BookUpdate update) {
    if (update.getBids() != null) {
      applyDeltas(bids, update.getBids());
      updateBestBid();
    }
    if (update.getAsks() != null) {
      applyDeltas(asks, update.getAsks());
      updateBestAsk();
    }
  }

  private void applyDeltas(TreeMap<Double, Double> book, List<OrderBookLevel> levels) {
    for (OrderBookLevel level : levels) {
      double price = Double.parseDouble(level.getPrice());
      double size = Double.parseDouble(level.getSize());

      // Polymarket matching engine sends size = 0 to indicate a price level has been fully
      // consumed/removed.
      if (size == 0.0) {
        book.remove(price);
      } else {
        book.put(price, size);
      }
    }
  }

  private void updateBestBid() {
    if (bids.isEmpty()) {
      bestBidPrice = 0.0;
      bestBidSize = 0.0;
    } else {
      Map.Entry<Double, Double> first = bids.firstEntry();
      bestBidPrice = first.getKey();
      bestBidSize = first.getValue();
    }
  }

  private void updateBestAsk() {
    if (asks.isEmpty()) {
      bestAskPrice = Double.MAX_VALUE;
      bestAskSize = 0.0;
    } else {
      Map.Entry<Double, Double> first = asks.firstEntry();
      bestAskPrice = first.getKey();
      bestAskSize = first.getValue();
    }
  }

  public String getTokenId() {
    return tokenId;
  }

  public double getBestAskPrice() {
    return bestAskPrice;
  }

  public double getBestAskSize() {
    return bestAskSize;
  }

  public double getBestBidPrice() {
    return bestBidPrice;
  }

  public double getBestBidSize() {
    return bestBidSize;
  }

  /**
   * Calculates the blended cost (average price per share) to buy the requested size by walking up
   * the order book.
   *
   * @param requestedSize the total size to buy
   * @return the blended price, or Double.MAX_VALUE if there is insufficient liquidity
   */
  public double getBlendedAskPrice(double requestedSize) {
    if (requestedSize <= 0) return 0.0;

    double remainingSize = requestedSize;
    double totalCost = 0.0;

    for (Map.Entry<Double, Double> entry : asks.entrySet()) {
      double price = entry.getKey();
      double available = entry.getValue();

      if (available >= remainingSize) {
        totalCost += price * remainingSize;
        remainingSize = 0;
        break;
      } else {
        totalCost += price * available;
        remainingSize -= available;
      }
    }

    if (remainingSize > 0) {
      return Double.MAX_VALUE; // Not enough liquidity
    }

    return totalCost / requestedSize;
  }
}
