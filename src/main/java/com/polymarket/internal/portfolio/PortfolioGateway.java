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
import com.polymarket.portfolio.MarketReference;
import com.polymarket.portfolio.Notification;
import com.polymarket.portfolio.NotificationKind;
import com.polymarket.portfolio.NotificationPayload;
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

    // protocol/constraints.json pagination: /trades limit <= 500, offset <= 1000.
    private static final int MAX_TRADES_LIMIT = 500;
    private static final int MAX_TRADES_OFFSET = 1_000;

    // /activity shares that pinned page contract; the live spec is looser, so this is the safe floor.
    private static final int MAX_ACTIVITY_LIMIT = 500;
    private static final int MAX_ACTIVITY_OFFSET = 1_000;

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
    public List<Notification> notifications(ApiCredentials credentials, String address,
            int signatureType) throws IOException {
        // The L2 signature covers the path INCLUDING the query string.
        String path = "/notifications?signature_type=" + signatureType;
        HttpOutcome outcome = runtime.get(config.clobHost(), path, L2Attestation.headers(
                credentials, address, clock.instant().getEpochSecond(), "GET", path, null));
        if (!outcome.successful()) {
            throw new IOException("notifications read failed with HTTP " + outcome.status());
        }
        List<Notification> notifications = new ArrayList<>();
        runtime.parse(outcome.body()).forEach(node -> notifications.add(notification(node)));
        return List.copyOf(notifications);
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
