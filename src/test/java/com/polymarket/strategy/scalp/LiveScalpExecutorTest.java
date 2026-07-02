package com.polymarket.strategy.scalp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.polymarket.client.AsyncPolymarketClient;
import com.polymarket.model.OrderMarketCancelParams;
import com.polymarket.model.OrderResponse;
import com.polymarket.model.OrderType;
import com.polymarket.model.Side;
import com.polymarket.model.UserOrder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LiveScalpExecutorTest {

  private static final String MARKET = "0xmarket";
  private static final String TOKEN = "cheap-token";

  @Mock AsyncPolymarketClient client;
  LiveScalpExecutor executor;

  @BeforeEach
  void setUp() {
    executor = new LiveScalpExecutor(client);
  }

  private void stubPostOk() {
    when(client.createAndPostOrders(anyList(), any(OrderType.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                List.of(
                    OrderResponse.builder().success(true).orderID("oid-1").status("live").build())));
  }

  private void stubCancelOk() {
    when(client.cancelMarketOrders(any(OrderMarketCancelParams.class)))
        .thenReturn(CompletableFuture.completedFuture(Map.of()));
  }

  @Test
  void placeEntryPostsBuyGtcWithCorrectSharesAndTickRoundedPrice() {
    stubPostOk();
    executor.placeEntry(MARKET, TOKEN, 0.333, 50); // 0.333 -> nearest 0.01 tick = 0.33

    ArgumentCaptor<List<UserOrder>> orders = ArgumentCaptor.forClass(List.class);
    verify(client).createAndPostOrders(orders.capture(), org.mockito.ArgumentMatchers.eq(OrderType.GTC));
    UserOrder o = orders.getValue().get(0);
    assertEquals(Side.BUY, o.side());
    assertEquals(0, new BigDecimal("0.33").compareTo(o.price()));
    // shares = floor(50 / 0.33, 2) = 151.51
    assertEquals(0, new BigDecimal("151.51").compareTo(o.size()));
  }

  @Test
  void placeExitPostsSellGtc() {
    stubPostOk();
    executor.placeExit(MARKET, TOKEN, 0.38, 200);

    ArgumentCaptor<List<UserOrder>> orders = ArgumentCaptor.forClass(List.class);
    verify(client).createAndPostOrders(orders.capture(), org.mockito.ArgumentMatchers.eq(OrderType.GTC));
    UserOrder o = orders.getValue().get(0);
    assertEquals(Side.SELL, o.side());
    assertEquals(0, new BigDecimal("0.38").compareTo(o.price()));
    assertEquals(0, new BigDecimal("200").compareTo(o.size()));
  }

  @Test
  void skipsOrderBelowDollarMinimum() {
    // 0.02 * floor(1/0.02=50 -> wait) : use tiny notional. sizeUsdc=0.5 at px 0.40 -> 1.25 shares -> $0.5 < $1
    executor.placeEntry(MARKET, TOKEN, 0.40, 0.5);
    verify(client, never()).createAndPostOrders(anyList(), any(OrderType.class));
  }

  @Test
  void flattenCancelsRestingThenPostsFakSell() {
    stubCancelOk();
    stubPostOk();
    executor.marketFlatten(MARKET, TOKEN, 0.34, 200);

    // cancel first
    verify(client).cancelMarketOrders(any(OrderMarketCancelParams.class));
    // then a FAK sell
    ArgumentCaptor<List<UserOrder>> orders = ArgumentCaptor.forClass(List.class);
    verify(client).createAndPostOrders(orders.capture(), org.mockito.ArgumentMatchers.eq(OrderType.FAK));
    UserOrder o = orders.getValue().get(0);
    assertEquals(Side.SELL, o.side());
    assertEquals(0, new BigDecimal("0.34").compareTo(o.price()));
    assertEquals(0, new BigDecimal("200").compareTo(o.size()));
  }

  @Test
  void flattenSellPriceRoundsDownToStayMarketable() {
    stubCancelOk();
    stubPostOk();
    executor.marketFlatten(MARKET, TOKEN, 0.349, 200); // 0.349 -> down to 0.34, not up to 0.35

    ArgumentCaptor<List<UserOrder>> orders = ArgumentCaptor.forClass(List.class);
    verify(client).createAndPostOrders(orders.capture(), org.mockito.ArgumentMatchers.eq(OrderType.FAK));
    assertEquals(0, new BigDecimal("0.34").compareTo(orders.getValue().get(0).price()));
  }

  @Test
  void duplicateFlattenSuppressedWhileInFlight() {
    // cancel future never completes -> the first flatten stays "in flight"
    when(client.cancelMarketOrders(any(OrderMarketCancelParams.class)))
        .thenReturn(new CompletableFuture<>());

    executor.marketFlatten(MARKET, TOKEN, 0.34, 200);
    executor.marketFlatten(MARKET, TOKEN, 0.34, 200); // duplicate, must be suppressed

    verify(client, times(1)).cancelMarketOrders(any(OrderMarketCancelParams.class));
  }

  @Test
  void cancelAllCancelsMarketOrders() {
    stubCancelOk();
    executor.cancelAll(MARKET, TOKEN);
    verify(client).cancelMarketOrders(any(OrderMarketCancelParams.class));
  }

  @Test
  void flattenClearsInFlightFlagWhenComplete() {
    stubCancelOk();
    stubPostOk();
    executor.marketFlatten(MARKET, TOKEN, 0.34, 200); // completes synchronously (completed futures)
    executor.marketFlatten(MARKET, TOKEN, 0.34, 100); // flag cleared -> second one proceeds

    // Two full flatten cycles => cancel called twice.
    verify(client, times(2)).cancelMarketOrders(any(OrderMarketCancelParams.class));
    verify(client, times(2)).createAndPostOrders(anyList(), org.mockito.ArgumentMatchers.eq(OrderType.FAK));
  }
}
