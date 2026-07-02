package com.polymarket.strategy.scalp;

import com.polymarket.client.AsyncPolymarketClient;
import com.polymarket.model.OrderMarketCancelParams;
import com.polymarket.model.OrderResponse;
import com.polymarket.model.OrderType;
import com.polymarket.model.Side;
import com.polymarket.model.UserOrder;
import com.polymarket.util.PriceUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Live {@link ScalpExecutor}: turns scalp decisions into real CLOB orders via {@link
 * AsyncPolymarketClient}. Real-money concerns handled here (not in the pure strategy):
 *
 * <ul>
 *   <li>Prices are tick-rounded (entries/exits to nearest tick, flatten sells rounded <em>down</em>
 *       so the marketable limit stays at or below the bid and actually fills).
 *   <li>Orders below Polymarket's ~$1 minimum are skipped rather than rejected.
 *   <li>Flatten cancels any resting entry/exit for the market first, then posts a FAK sell — this
 *       prevents the resting take-profit and the flatten from both filling (an oversell).
 *   <li>A flatten already in flight for a market suppresses duplicate flatten commands (the
 *       strategy re-emits MARKET_FLATTEN every tick until it sees the position drop); the next
 *       real position update re-triggers for any residual.
 * </ul>
 *
 * <p>All calls are non-blocking: {@link AsyncPolymarketClient} returns immediately, so this never
 * stalls the worker's lock.
 */
public class LiveScalpExecutor implements ScalpExecutor {

  private static final Logger log = LoggerFactory.getLogger(LiveScalpExecutor.class);

  /** Polymarket rejects orders whose price*size is below ~$1. */
  private static final BigDecimal MIN_ORDER_USD = BigDecimal.ONE;

  /** Default price tick for the BTC/ETH up/down markets; override per market if a book uses 0.001. */
  static final BigDecimal DEFAULT_TICK_SIZE = new BigDecimal("0.01");

  private final AsyncPolymarketClient client;
  private final BigDecimal tickSize;

  /** Markets with a flatten currently in flight — suppresses duplicate flatten submissions. */
  private final Set<String> flattening = ConcurrentHashMap.newKeySet();

  public LiveScalpExecutor(AsyncPolymarketClient client) {
    this(client, DEFAULT_TICK_SIZE);
  }

  public LiveScalpExecutor(AsyncPolymarketClient client, BigDecimal tickSize) {
    this.client = client;
    this.tickSize = tickSize;
  }

  @Override
  public void placeEntry(String marketId, String tokenId, double price, double sizeUsdc) {
    BigDecimal px = tickRound(price, "nearest");
    if (px.signum() <= 0) {
      log.warn("[scalp] skip entry token={} bad price={}", tokenId, price);
      return;
    }
    BigDecimal shares = floorShares(BigDecimal.valueOf(sizeUsdc).divide(px, 8, RoundingMode.DOWN));
    if (belowMinimum(px, shares)) {
      log.warn("[scalp] skip entry token={} px={} size={} below ${} minimum", tokenId, px, shares, MIN_ORDER_USD);
      return;
    }
    post("entry", marketId, tokenId, Side.BUY, px, shares);
  }

  @Override
  public void placeExit(String marketId, String tokenId, double price, double sizeShares) {
    BigDecimal px = tickRound(price, "nearest");
    BigDecimal shares = floorShares(BigDecimal.valueOf(sizeShares));
    if (belowMinimum(px, shares)) {
      log.warn("[scalp] skip exit token={} px={} size={} below ${} minimum", tokenId, px, shares, MIN_ORDER_USD);
      return;
    }
    post("exit", marketId, tokenId, Side.SELL, px, shares);
  }

  @Override
  public void marketFlatten(String marketId, String tokenId, double bidPrice, double sizeShares) {
    if (!flattening.add(marketId)) {
      log.debug("[scalp] flatten already in flight for market={}, skipping duplicate", marketId);
      return;
    }
    BigDecimal px = tickRound(bidPrice, "down"); // stay at/below the bid so the FAK fills
    BigDecimal shares = floorShares(BigDecimal.valueOf(sizeShares));
    if (px.signum() <= 0 || shares.signum() <= 0) {
      log.warn("[scalp] cannot flatten token={} bid={} size={}", tokenId, bidPrice, sizeShares);
      flattening.remove(marketId);
      return;
    }
    // Cancel any resting entry/exit first so we don't oversell, then take the bid with a FAK sell.
    client
        .cancelMarketOrders(OrderMarketCancelParams.builder().market(marketId).build())
        .whenComplete((r, err) -> postFlatten(marketId, tokenId, px, shares));
  }

  private void postFlatten(String marketId, String tokenId, BigDecimal px, BigDecimal shares) {
    UserOrder order =
        UserOrder.builder().tokenID(tokenId).side(Side.SELL).price(px).size(shares).build();
    client
        .createAndPostOrders(List.of(order), OrderType.FAK)
        .whenComplete(
            (responses, err) -> {
              flattening.remove(marketId);
              if (err != null) {
                log.error("[scalp] flatten FAILED token={} px={} size={}: {}", tokenId, px, shares, err.toString());
                return;
              }
              logResult("flatten", tokenId, px, shares, responses);
            });
  }

  @Override
  public void cancelAll(String marketId, String tokenId) {
    client
        .cancelMarketOrders(OrderMarketCancelParams.builder().market(marketId).build())
        .whenComplete(
            (r, err) -> {
              if (err != null) {
                log.error("[scalp] cancelAll FAILED market={}: {}", marketId, err.toString());
              } else {
                log.info("[scalp] cancelled resting orders for market={}", marketId);
              }
            });
  }

  private void post(String kind, String marketId, String tokenId, Side side, BigDecimal px, BigDecimal shares) {
    UserOrder order =
        UserOrder.builder().tokenID(tokenId).side(side).price(px).size(shares).build();
    log.info("[scalp] {} {} token={} px={} size={}", kind, side, tokenId, px, shares);
    client
        .createAndPostOrders(List.of(order), OrderType.GTC)
        .whenComplete(
            (responses, err) -> {
              if (err != null) {
                log.error("[scalp] {} FAILED token={} px={} size={}: {}", kind, tokenId, px, shares, err.toString());
                return;
              }
              logResult(kind, tokenId, px, shares, responses);
            });
  }

  private void logResult(String kind, String tokenId, BigDecimal px, BigDecimal shares, List<OrderResponse> responses) {
    if (responses == null || responses.isEmpty()) {
      log.error("[scalp] {} token={} returned no response", kind, tokenId);
      return;
    }
    OrderResponse r = responses.get(0);
    if (r.success()) {
      log.info("[scalp] {} OK token={} px={} size={} orderId={} status={}", kind, tokenId, px, shares, r.orderID(), r.status());
    } else {
      log.error("[scalp] {} REJECTED token={} px={} size={}: {}", kind, tokenId, px, shares, r.errorMsg());
    }
  }

  private BigDecimal tickRound(double price, String mode) {
    return PriceUtils.tickRound(BigDecimal.valueOf(price), tickSize, mode).stripTrailingZeros();
  }

  private static BigDecimal floorShares(BigDecimal shares) {
    return shares.setScale(2, RoundingMode.DOWN);
  }

  private static boolean belowMinimum(BigDecimal px, BigDecimal shares) {
    return px.multiply(shares).compareTo(MIN_ORDER_USD) < 0;
  }
}
