package com.polymarket;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.Authentication;
import com.polymarket.authentication.SigningAuthority;
import com.polymarket.builders.Builders;
import com.polymarket.internal.authentication.AuthenticationGateway;
import com.polymarket.internal.builders.BuildersGateway;
import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.internal.markets.MarketsGateway;
import com.polymarket.internal.markets.OrderBookGateway;
import com.polymarket.internal.operations.HeartbeatGateway;
import com.polymarket.internal.operations.OperationsGateway;
import com.polymarket.internal.portfolio.PortfolioGateway;
import com.polymarket.internal.rewards.RewardsGateway;
import com.polymarket.internal.streaming.RtdsGateway;
import com.polymarket.internal.streaming.StreamingGateway;
import com.polymarket.internal.trading.Eip712OrderSigner;
import com.polymarket.internal.trading.TradeReaderGateway;
import com.polymarket.internal.trading.TradingGateway;
import com.polymarket.markets.Markets;
import com.polymarket.markets.OrderBooks;
import com.polymarket.operations.GeoblockStatus;
import com.polymarket.operations.ServerTime;
import com.polymarket.operations.ServiceHealth;
import com.polymarket.portfolio.Portfolio;
import com.polymarket.rewards.Rewards;
import com.polymarket.streaming.Rtds;
import com.polymarket.streaming.Streaming;
import com.polymarket.trading.Trading;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Entry point to the Polymarket SDK. Construction needs no credentials and performs no
 * network call; it is thread-safe and closing it releases owned resources once.
 */
public final class Polymarket implements AutoCloseable {

    private final PolymarketConfig config;
    private final HttpRuntime runtime;
    private final SigningAuthority authority;
    private final OperationsGateway operations;
    private final Authentication authentication;
    private final Markets markets;
    private final OrderBooks orderBooks;
    private final Portfolio portfolio;
    private final Rewards rewards;
    private final Builders builders;
    private final Trading trading;
    private final StreamingGateway streamingGateway;
    private final Streaming streaming;
    private final RtdsGateway rtdsGateway;
    private final Rtds rtds;
    private final HeartbeatGateway heartbeat;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private Polymarket(PolymarketConfig config, HttpRuntime runtime, SigningAuthority authority,
            Clock clock) {
        this.config = config;
        this.runtime = runtime;
        this.authority = authority;
        this.operations = new OperationsGateway(config, runtime);
        this.authentication = new Authentication(authority,
                new AuthenticationGateway(config, runtime, clock));
        this.markets = new Markets(new MarketsGateway(config, runtime));
        this.orderBooks = new OrderBooks(new OrderBookGateway(config, runtime));
        this.portfolio = new Portfolio(authority, new PortfolioGateway(config, runtime, clock));
        this.rewards = new Rewards(authority, new RewardsGateway(config, runtime, clock));
        this.builders = new Builders(authority, new BuildersGateway(config, runtime, clock));
        TradingGateway tradingGateway = new TradingGateway(config, runtime, clock);
        this.trading = new Trading(new Eip712OrderSigner(), tradingGateway, tradingGateway,
                new TradeReaderGateway(config, runtime, clock), clock);
        this.streamingGateway = new StreamingGateway();
        this.streaming = new Streaming(streamingGateway, authority);
        this.rtdsGateway = new RtdsGateway();
        this.rtds = new Rtds(rtdsGateway);
        this.heartbeat = new HeartbeatGateway(config, runtime, clock);
    }

    public static Polymarket withDefaults() {
        return with(PolymarketConfig.defaults());
    }

    public static Polymarket with(PolymarketConfig config) {
        return with(config, SigningAuthority.none());
    }

    public static Polymarket with(PolymarketConfig config, SigningAuthority authority) {
        Objects.requireNonNull(config, "config");
        return new Polymarket(config, new HttpRuntime(config.connectTimeout(),
                config.requestTimeout(), config.readRetryPolicy()), authority, Clock.systemUTC());
    }

    /** Test seam: lets a caller supply a runtime whose backoff does not sleep. */
    static Polymarket with(PolymarketConfig config, HttpRuntime runtime) {
        return with(config, runtime, SigningAuthority.none(), Clock.systemUTC());
    }

    static Polymarket with(PolymarketConfig config, HttpRuntime runtime,
            SigningAuthority authority, Clock clock) {
        return new Polymarket(Objects.requireNonNull(config), Objects.requireNonNull(runtime),
                Objects.requireNonNull(authority), Objects.requireNonNull(clock));
    }

    public PolymarketConfig config() {
        return config;
    }

    /** API-key lifecycle. Reachable without credentials; each call checks its own authority. */
    public Authentication authentication() {
        if (closed.get()) throw new IllegalStateException("Polymarket is closed");
        return authentication;
    }

    /** Canonical market discovery over Gamma. Needs no credentials. */
    public Markets markets() {
        if (closed.get()) throw new IllegalStateException("Polymarket is closed");
        return markets;
    }

    /** Live CLOB order books. Needs no credentials. */
    public OrderBooks orderBooks() {
        if (closed.get()) throw new IllegalStateException("Polymarket is closed");
        return orderBooks;
    }

    /** Account state: positions, trades, activity and notifications. */
    public Portfolio portfolio() {
        if (closed.get()) throw new IllegalStateException("Polymarket is closed");
        return portfolio;
    }

    /** Market reward programmes and user earnings, one typed cursor page at a time. */
    public Rewards rewards() {
        if (closed.get()) throw new IllegalStateException("Polymarket is closed");
        return rewards;
    }

    /** Builder credential lifecycle and builder-attributed trade reads. Every call needs L2. */
    public Builders builders() {
        if (closed.get()) throw new IllegalStateException("Polymarket is closed");
        return builders;
    }

    /** V2 token order signing and submission. Signing needs no credentials; submission needs L2. */
    public Trading trading() {
        if (closed.get()) throw new IllegalStateException("Polymarket is closed");
        return trading;
    }

    /** Live CLOB market and user event streams. The user channel needs L2 credentials. */
    public Streaming streaming() {
        if (closed.get()) throw new IllegalStateException("Polymarket is closed");
        return streaming;
    }

    /** Real-Time Data Service prices and comments. Unauthenticated; owned and closed by this root. */
    public Rtds rtds() {
        if (closed.get()) throw new IllegalStateException("Polymarket is closed");
        return rtds;
    }

    public ServerTime serverTime() throws IOException {
        return open().serverTime();
    }

    /** Probes every service; an unreachable service is reported, not thrown. */
    public List<ServiceHealth> health() {
        return open().health();
    }

    public GeoblockStatus geoblock() throws IOException {
        return open().geoblock();
    }

    /** Starts the CLOB dead-man-switch heartbeat at the documented 5 s interval. Idle until called. */
    public void startHeartbeat() {
        startHeartbeat(HeartbeatGateway.DEFAULT_INTERVAL);
    }

    /** Starts the heartbeat at a custom interval; needs L2 credentials. */
    public void startHeartbeat(Duration interval) {
        if (closed.get()) throw new IllegalStateException("Polymarket is closed");
        ApiCredentials credentials = authority.requireApiCredentials("heartbeat");
        String address = authority.requireAccountSigner("heartbeat");
        heartbeat.start(interval, credentials, address);
    }

    /** Cancels the scheduled heartbeat tick. Does nothing if not currently active. */
    public void stopHeartbeat() {
        heartbeat.stop();
    }

    /** True while the heartbeat tick is scheduled. */
    public boolean isHeartbeatActive() {
        return heartbeat.isActive();
    }

    private OperationsGateway open() {
        if (closed.get()) throw new IllegalStateException("Polymarket is closed");
        return operations;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            heartbeat.close();
            streaming.close();
            streamingGateway.close();
            rtds.close();
            rtdsGateway.close();
            runtime.close();
        }
    }
}
