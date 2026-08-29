package com.polymarket.internal.portfolio;

import com.fasterxml.jackson.databind.JsonNode;
import com.polymarket.PolymarketConfig;
import com.polymarket.authentication.ApiCredentials;
import com.polymarket.internal.authentication.L2Attestation;
import com.polymarket.internal.http.HttpOutcome;
import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.portfolio.ActivityKind;
import com.polymarket.portfolio.ActivityQuery;
import com.polymarket.portfolio.ActivityRecord;
import com.polymarket.portfolio.AssetType;
import com.polymarket.portfolio.BalanceSnapshot;
import com.polymarket.portfolio.ComboLegSnapshot;
import com.polymarket.portfolio.ComboPositionQuery;
import com.polymarket.portfolio.ComboPositionSnapshot;
import com.polymarket.portfolio.ComboStatus;
import com.polymarket.portfolio.MarketReference;
import com.polymarket.portfolio.Notification;
import com.polymarket.portfolio.NotificationKind;
import com.polymarket.portfolio.NotificationPayload;
import com.polymarket.portfolio.OpenOrder;
import com.polymarket.portfolio.OpenOrderPage;
import com.polymarket.portfolio.OpenOrderQuery;
import com.polymarket.portfolio.OrderCursor;
import com.polymarket.portfolio.OrderLifetime;
import com.polymarket.portfolio.OrderStatus;
import com.polymarket.portfolio.PageCursor;
import com.polymarket.portfolio.PortfolioLedger;
import com.polymarket.portfolio.PortfolioPage;
import com.polymarket.portfolio.PositionQuery;
import com.polymarket.portfolio.PositionSnapshot;
import com.polymarket.portfolio.PositionValuation;
import com.polymarket.portfolio.TradeQuery;
import com.polymarket.portfolio.TradeRecord;
import com.polymarket.portfolio.TradedSide;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Data API transport for portfolio reads; maps wire JSON to domain values and nothing else. */
public final class PortfolioGateway implements PortfolioLedger {

    private static final Map<String, String> ACCEPT_JSON = Map.of("Accept", "application/json");

    // data-openapi.yaml GET /positions: limit <= 500, offset <= 10000.
    private static final int MAX_POSITIONS_LIMIT = 500;
    private static final int MAX_POSITIONS_OFFSET = 10_000;

    // constraints.json pagination.dataApi: the 2025-08-26 changelog's 500/1000 is stale.
    private static final int MAX_TRADES_LIMIT = 10_000;
    private static final int MAX_TRADES_OFFSET = 10_000;

    private static final int MAX_ACTIVITY_LIMIT = 500;
    private static final int MAX_ACTIVITY_OFFSET = 5_000;

    // data-openapi.yaml GET /v1/positions/combos: limit <= 1000, offset <= 100000.
    private static final int MAX_COMBOS_LIMIT = 1_000;
    private static final int MAX_COMBOS_OFFSET = 100_000;

    private final PolymarketConfig config;
    private final HttpRuntime runtime;
    private final Clock clock;

    public PortfolioGateway(PolymarketConfig config, HttpRuntime runtime, Clock clock) {
        this.config = config;
        this.runtime = runtime;
        this.clock = clock;
    }

    @Override
    public PortfolioPage<PositionSnapshot> positions(PositionQuery query, PageCursor cursor)
            throws IOException {
        requireWithin(cursor, MAX_POSITIONS_LIMIT, MAX_POSITIONS_OFFSET, "/positions");
        QueryString params = new QueryString();
        params.add("user", query.user());
        if (!query.conditionIds().isEmpty()) {
            params.add("market", String.join(",", query.conditionIds()));
        }
        query.sizeThreshold().ifPresent(v -> params.add("sizeThreshold", v.toPlainString()));
        query.redeemable().ifPresent(v -> params.add("redeemable", v.toString()));
        query.mergeable().ifPresent(v -> params.add("mergeable", v.toString()));
        query.includeArchived().ifPresent(v -> params.add("includeArchived", v.toString()));
        params.add("limit", String.valueOf(cursor.limit()));
        params.add("offset", String.valueOf(cursor.offset()));

        Instant observedAt = clock.instant();
        List<PositionSnapshot> positions = new ArrayList<>();
        read("/positions" + params).forEach(node -> positions.add(position(node, observedAt)));
        return page(positions, cursor, MAX_POSITIONS_OFFSET);
    }

    @Override
    public PortfolioPage<TradeRecord> trades(TradeQuery query, PageCursor cursor)
            throws IOException {
        requireWithin(cursor, MAX_TRADES_LIMIT, MAX_TRADES_OFFSET, "/trades");
        QueryString params = new QueryString();
        query.user().ifPresent(v -> params.add("user", v));
        if (!query.conditionIds().isEmpty()) {
            params.add("market", String.join(",", query.conditionIds()));
        }
        query.side().ifPresent(v -> params.add("side", v.name()));
        query.takerOnly().ifPresent(v -> params.add("takerOnly", v.toString()));
        query.from().ifPresent(v -> params.add("start", String.valueOf(v.getEpochSecond())));
        query.to().ifPresent(v -> params.add("end", String.valueOf(v.getEpochSecond())));
        params.add("limit", String.valueOf(cursor.limit()));
        params.add("offset", String.valueOf(cursor.offset()));

        List<TradeRecord> trades = new ArrayList<>();
        read("/trades" + params).forEach(node -> trades.add(trade(node)));
        return page(trades, cursor, MAX_TRADES_OFFSET);
    }

    @Override
    public PortfolioPage<ActivityRecord> activity(ActivityQuery query, PageCursor cursor)
            throws IOException {
        requireWithin(cursor, MAX_ACTIVITY_LIMIT, MAX_ACTIVITY_OFFSET, "/activity");
        QueryString params = new QueryString();
        params.add("user", query.user());
        if (!query.kinds().isEmpty()) {
            params.add("type", query.kinds().stream().map(Enum::name)
                    .collect(Collectors.joining(",")));
        }
        query.includeDepositsAndWithdrawals().ifPresent(
                v -> params.add("excludeDepositsWithdrawals", String.valueOf(!v)));
        query.side().ifPresent(v -> params.add("side", v.name()));
        query.from().ifPresent(v -> params.add("start", String.valueOf(v.getEpochSecond())));
        query.to().ifPresent(v -> params.add("end", String.valueOf(v.getEpochSecond())));
        params.add("limit", String.valueOf(cursor.limit()));
        params.add("offset", String.valueOf(cursor.offset()));

        List<ActivityRecord> rows = new ArrayList<>();
        read("/activity" + params).forEach(node -> rows.add(activity(node)));
        return page(rows, cursor, MAX_ACTIVITY_OFFSET);
    }

    @Override
    public PortfolioPage<ComboPositionSnapshot> comboPositions(ComboPositionQuery query,
            PageCursor cursor) throws IOException {
        requireWithin(cursor, MAX_COMBOS_LIMIT, MAX_COMBOS_OFFSET, "/v1/positions/combos");
        QueryString params = new QueryString();
        params.add("user", query.user());
        if (!query.statuses().isEmpty()) {
            params.add("status", query.statuses().stream().map(Enum::name)
                    .collect(Collectors.joining(",")));
        }
        if (!query.comboConditionIds().isEmpty()) {
            params.add("market_id", String.join(",", query.comboConditionIds()));
        }
        params.add("limit", String.valueOf(cursor.limit()));
        params.add("offset", String.valueOf(cursor.offset()));

        Instant observedAt = clock.instant();
        JsonNode body = read("/v1/positions/combos" + params);
        List<ComboPositionSnapshot> combos = new ArrayList<>();
        body.path("combos").forEach(node -> combos.add(comboPosition(node, observedAt)));
        // This feed states exhaustion outright, so page fullness is never guessed at.
        if (!body.path("pagination").path("has_more").asBoolean(false)) {
            return PortfolioPage.lastPage(combos);
        }
        PageCursor next = cursor.next();
        return next.offset() > MAX_COMBOS_OFFSET
                ? PortfolioPage.atPaginationLimit(combos)
                : PortfolioPage.withNext(combos, next);
    }

    private static ComboPositionSnapshot comboPosition(JsonNode node, Instant observedAt) {
        List<ComboLegSnapshot> legs = new ArrayList<>();
        node.path("legs").forEach(leg -> legs.add(comboLeg(leg)));
        return new ComboPositionSnapshot(
                required(node, "combo_condition_id"),
                required(node, "combo_position_id"),
                text(node, "user_address"),
                requiredDecimal(node, "shares_balance"),
                new ComboStatus(required(node, "status")),
                decimal(node, "entry_avg_price_usdc"),
                decimal(node, "entry_cost_usdc"),
                decimal(node, "realized_payout_usdc"),
                decimal(node, "total_cost_usdc"),
                decimal(node, "gross_entry_cost_usdc"),
                decimal(node, "entry_fees_usdc"),
                instant(node, "first_entry_at"),
                instant(node, "resolved_at"),
                instant(node, "updated_at"),
                legs,
                observedAt);
    }

    private static ComboLegSnapshot comboLeg(JsonNode node) {
        return new ComboLegSnapshot(
                node.path("leg_index").asInt(),
                required(node, "leg_position_id"),
                text(node, "leg_condition_id"),
                integer(node, "leg_outcome_index"),
                text(node, "leg_outcome_label"),
                new ComboStatus(required(node, "leg_status")),
                instant(node, "leg_resolved_at"),
                decimal(node, "leg_current_price"));
    }

    @Override
    public OpenOrderPage openOrders(ApiCredentials credentials, String address,
            OpenOrderQuery query, OrderCursor cursor) throws IOException {
        QueryString params = new QueryString();
        query.orderId().ifPresent(v -> params.add("id", v));
        query.conditionId().ifPresent(v -> params.add("market", v));
        query.assetId().ifPresent(v -> params.add("asset_id", v));
        params.add("next_cursor", cursor.value());

        String path = "/data/orders" + params;
        JsonNode body = readL2(credentials, address, path, "open order");
        List<OpenOrder> orders = new ArrayList<>();
        body.path("data").forEach(node -> orders.add(openOrder(node)));
        return new OpenOrderPage(orders,
                OrderCursor.next(cursor, text(body, "next_cursor").orElse(null)),
                body.path("limit").asInt(), body.path("count").asInt());
    }

    private static OpenOrder openOrder(JsonNode node) {
        return new OpenOrder(
                required(node, "id"),
                new OrderStatus(required(node, "status")),
                text(node, "owner"),
                text(node, "maker_address"),
                required(node, "market"),
                required(node, "asset_id"),
                new TradedSide(required(node, "side")),
                requiredDecimal(node, "original_size"),
                requiredDecimal(node, "size_matched"),
                requiredDecimal(node, "price"),
                text(node, "outcome"),
                new OrderLifetime(required(node, "order_type")),
                // "0" is the documented GTC value: no expiry, not an epoch.
                decimal(node, "expiration").filter(v -> v.signum() > 0)
                        .map(v -> Instant.ofEpochSecond(v.longValueExact())),
                associatedTradeIds(node),
                requiredInstant(node, "created_at"));
    }

    private static List<String> associatedTradeIds(JsonNode node) {
        List<String> ids = new ArrayList<>();
        node.path("associate_trades").forEach(id -> ids.add(id.asText()));
        return ids;
    }

    @Override
    public BalanceSnapshot balance(ApiCredentials credentials, String address, AssetType assetType,
            Optional<String> tokenId, int signatureType) throws IOException {
        QueryString params = new QueryString();
        params.add("asset_type", assetType.name());
        tokenId.ifPresent(v -> params.add("token_id", v));
        params.add("signature_type", String.valueOf(signatureType));

        String path = "/balance-allowance" + params;
        Instant observedAt = clock.instant();
        JsonNode body = readL2(credentials, address, path, "balance");
        Map<String, BigDecimal> allowances = new LinkedHashMap<>();
        body.path("allowances").fields().forEachRemaining(
                entry -> allowances.put(entry.getKey(), fixedMath(entry.getValue().asText())));
        return new BalanceSnapshot(assetType, tokenId,
                fixedMath(required(body, "balance")), allowances, observedAt);
    }

    /** clob-openapi.yaml documents balances and allowances as 6-decimal fixed math (pUSD). */
    private static BigDecimal fixedMath(String wireValue) {
        return new BigDecimal(wireValue).movePointLeft(6);
    }

    @Override
    public List<Notification> notifications(ApiCredentials credentials, String address,
            int signatureType) throws IOException {
        // The L2 signature covers the path INCLUDING the query string.
        String path = "/notifications?signature_type=" + signatureType;
        List<Notification> notifications = new ArrayList<>();
        readL2(credentials, address, path, "notifications")
                .forEach(node -> notifications.add(notification(node)));
        return List.copyOf(notifications);
    }

    private JsonNode readL2(ApiCredentials credentials, String address, String path, String what)
            throws IOException {
        HttpOutcome outcome = runtime.get(config.clobHost(), path, L2Attestation.headers(
                credentials, address, clock.instant().getEpochSecond(), "GET", path, null));
        if (!outcome.successful()) {
            throw new IOException(what + " read failed with HTTP " + outcome.status());
        }
        return runtime.parse(outcome.body());
    }

    private static Notification notification(JsonNode node) {
        JsonNode payload = node.path("payload");
        return new Notification(
                node.path("id").asLong(),
                text(node, "owner"),
                new NotificationKind(node.path("type").asInt()),
                new NotificationPayload(
                        text(payload, "order_id"),
                        text(payload, "market"),
                        text(payload, "asset_id"),
                        text(payload, "side").map(TradedSide::new),
                        decimal(payload, "price"),
                        decimal(payload, "original_size"),
                        decimal(payload, "matched_size"),
                        decimal(payload, "remaining_size"),
                        text(payload, "outcome"),
                        integer(payload, "outcome_index")),
                requiredInstant(node, "timestamp"));
    }

    private static ActivityRecord activity(JsonNode node) {
        return new ActivityRecord(
                text(node, "proxyWallet"),
                new ActivityKind(required(node, "type")),
                requiredInstant(node, "timestamp"),
                text(node, "conditionId"),
                text(node, "asset"),
                decimal(node, "size"),
                decimal(node, "usdcSize"),
                decimal(node, "price"),
                text(node, "side").map(TradedSide::new),
                text(node, "transactionHash"),
                flag(node, "isCombo"),
                marketReference(node));
    }

    private static TradeRecord trade(JsonNode node) {
        return new TradeRecord(
                text(node, "proxyWallet"),
                new TradedSide(required(node, "side")),
                required(node, "asset"),
                required(node, "conditionId"),
                requiredDecimal(node, "size"),
                requiredDecimal(node, "price"),
                requiredInstant(node, "timestamp"),
                text(node, "transactionHash"),
                marketReference(node));
    }

    private PositionSnapshot position(JsonNode node, Instant observedAt) {
        return new PositionSnapshot(
                required(node, "asset"),
                required(node, "conditionId"),
                text(node, "proxyWallet"),
                requiredDecimal(node, "size"),
                flag(node, "redeemable"),
                flag(node, "mergeable"),
                flag(node, "negativeRisk"),
                instant(node, "endDate"),
                new PositionValuation(
                        decimal(node, "avgPrice"),
                        decimal(node, "curPrice"),
                        decimal(node, "initialValue"),
                        decimal(node, "grossInitialValue"),
                        decimal(node, "entryFeesUsdc"),
                        decimal(node, "currentValue"),
                        decimal(node, "totalBought"),
                        decimal(node, "cashPnl"),
                        decimal(node, "percentPnl"),
                        decimal(node, "realizedPnl"),
                        decimal(node, "percentRealizedPnl")),
                marketReference(node),
                observedAt);
    }

    private static MarketReference marketReference(JsonNode node) {
        return new MarketReference(text(node, "title"), text(node, "slug"),
                text(node, "eventSlug"), text(node, "outcome"), integer(node, "outcomeIndex"));
    }

    /**
     * A short page means the source is exhausted; a full one continues until the endpoint's
     * documented offset budget is spent.
     */
    private static <T> PortfolioPage<T> page(List<T> items, PageCursor cursor, int maxOffset) {
        if (items.size() < cursor.limit()) return PortfolioPage.lastPage(items);
        PageCursor next = cursor.next();
        return next.offset() > maxOffset
                ? PortfolioPage.atPaginationLimit(items)
                : PortfolioPage.withNext(items, next);
    }

    private static void requireWithin(PageCursor cursor, int maxLimit, int maxOffset, String path) {
        if (cursor.limit() > maxLimit) {
            throw new IllegalArgumentException(
                    path + " allows a limit of at most " + maxLimit + ", got " + cursor.limit());
        }
        if (cursor.offset() > maxOffset) {
            throw new IllegalArgumentException(
                    path + " allows an offset of at most " + maxOffset + ", got " + cursor.offset());
        }
    }

    private JsonNode read(String path) throws IOException {
        HttpOutcome outcome = runtime.get(config.dataHost(), path, ACCEPT_JSON);
        if (!outcome.successful()) {
            throw new IOException("portfolio read " + path + " failed with HTTP " + outcome.status());
        }
        return runtime.parse(outcome.body());
    }

    private static String required(JsonNode node, String field) {
        return text(node, field).orElseThrow(() -> new IllegalStateException(
                "portfolio row is missing its " + field));
    }

    private static BigDecimal requiredDecimal(JsonNode node, String field) {
        return decimal(node, field).orElseThrow(() -> new IllegalStateException(
                "portfolio row is missing its " + field));
    }

    /** Absent stays absent. An empty string was sent and is kept, so it stays distinct from missing. */
    private static Optional<String> text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? Optional.empty() : Optional.of(value.asText());
    }

    /**
     * Keeps the wire text so an exact decimal survives a JSON number without a double round trip.
     * The Data API sends bare numbers; CLOB notification payloads quote theirs.
     */
    private static Optional<BigDecimal> decimal(JsonNode node, String field) {
        return text(node, field).filter(v -> !v.isBlank()).map(BigDecimal::new);
    }

    private static Optional<Boolean> flag(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isBoolean() ? Optional.empty() : Optional.of(value.asBoolean());
    }

    private static Optional<Integer> integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isIntegralNumber()
                ? Optional.empty() : Optional.of(value.asInt());
    }

    /** Epoch seconds on the wire; the Data API never sends a portfolio time as text. */
    private static Instant requiredInstant(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber()) {
            throw new IllegalStateException("portfolio row is missing its " + field);
        }
        return Instant.ofEpochSecond(value.asLong());
    }

    private static Optional<Instant> instant(JsonNode node, String field) {
        return text(node, field).filter(v -> !v.isBlank()).map(Instant::parse);
    }

    /** Builds the query string in a fixed order so a request is reproducible. */
    private static final class QueryString {
        private final StringBuilder text = new StringBuilder();

        void add(String name, String value) {
            text.append(text.isEmpty() ? '?' : '&').append(name).append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        }

        @Override
        public String toString() {
            return text.toString();
        }
    }
}
