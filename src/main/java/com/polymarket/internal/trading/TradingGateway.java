package com.polymarket.internal.trading;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.PolymarketConfig;
import com.polymarket.authentication.ApiCredentials;
import com.polymarket.internal.authentication.L2Attestation;
import com.polymarket.internal.http.HttpOutcome;
import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.markets.PositionId;
import com.polymarket.trading.BatchItem;
import com.polymarket.trading.BatchSubmissionOutcome;
import com.polymarket.trading.CancellationOutcome;
import com.polymarket.trading.OrderBatch;
import com.polymarket.trading.OrderPlacement;
import com.polymarket.trading.OrderSubmitter;
import com.polymarket.trading.SignedOrder;
import com.polymarket.trading.SubmissionOutcome;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Transport and protocol for {@code POST /order}, {@code POST /orders} and {@code DELETE /orders}.
 * Ground truth for the classification below: {@code api-reference/trade/post-a-new-order.md} and
 * {@code resources/error-codes.md} — the same rules the proven 1.0 {@code OrderSubmission} enforced,
 * carried over verbatim for the V2 shape.
 */
public final class TradingGateway implements OrderSubmitter, OrderBatch {

    private static final String ORDER_PATH = "/order";
    private static final String ORDERS_PATH = "/orders";

    /** Documented "not placed, try again" errors (Ticket 022/035 ground truth). */
    private static final List<String> RETRYABLE_ERRORS = List.of(
            "order timed out",
            "order match delayed due to market conditions",
            "the market is not yet ready to process new orders",
            "trading is currently cancel-only",
            "post-only mode: only post-only orders and cancels are allowed");

    /** HTTP 400 {@code "order {id} is invalid. Duplicated."}: proves an earlier attempt landed. */
    private static final String DUPLICATE_ORDER_MARKER = "is invalid. duplicated";

    private final PolymarketConfig config;
    private final HttpRuntime runtime;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();

    public TradingGateway(PolymarketConfig config, HttpRuntime runtime, Clock clock) {
        this.config = config;
        this.runtime = runtime;
        this.clock = clock;
    }

    @Override
    public SubmissionOutcome submit(SignedOrder order, OrderPlacement placement) {
        if (order.asset() instanceof PositionId) {
            throw new IllegalArgumentException(
                    "V3 Combo orders route through the RFQ Builder Gateway, not POST /order");
        }
        String body = wireBody(order, placement);
        HttpOutcome outcome;
        try {
            outcome = runtime.post(config.clobHost(), ORDER_PATH,
                    l2Headers(placement.credentials(), order.signer(), "POST", ORDER_PATH, body), body);
        } catch (IOException e) {
            return new SubmissionOutcome.Unknown(
                    Optional.empty(), transportMessage(e), Optional.of(e));
        }
        return classify(outcome);
    }

    private SubmissionOutcome classify(HttpOutcome outcome) {
        if (outcome.successful()) {
            return classifySuccessStatus(outcome);
        }
        String message = errorMessage(outcome.body());
        int status = outcome.status();
        if (status >= 400 && status < 500) {
            if (isDuplicateOrderError(message)) {
                return new SubmissionOutcome.Unknown(Optional.of(status), message, Optional.empty());
            }
            return new SubmissionOutcome.Rejected(status, message, isRetryableError(message));
        }
        if (status >= 500 && isRetryableError(message)) {
            // A documented server-side refusal states the order was NOT placed: definitive.
            return new SubmissionOutcome.Rejected(status, message, true);
        }
        // Every other non-2xx (generic 5xx, unexpected 3xx) is indeterminate.
        return new SubmissionOutcome.Unknown(Optional.of(status), message, Optional.empty());
    }

    private SubmissionOutcome classifySuccessStatus(HttpOutcome outcome) {
        JsonNode node = tryParse(outcome.body());
        if (node == null) {
            return new SubmissionOutcome.Unknown(
                    Optional.of(outcome.status()), "empty or unreadable order response body",
                    Optional.empty());
        }
        return classifyOrderNode(node, outcome.status());
    }

    /** Classifies one order-response object; reused for both a single body and each batch element. */
    private static SubmissionOutcome classifyOrderNode(JsonNode node, int httpStatus) {
        // Anything that is not a documented order object states nothing, so it cannot be a rejection.
        if (!isOrderResponse(node)) {
            return new SubmissionOutcome.Unknown(Optional.of(httpStatus),
                    "success response is not a documented order object", Optional.empty());
        }
        String errorMsg = blankToNull(node.path("errorMsg").asText(null));
        boolean success = node.path("success").asBoolean(false);

        if (!success) {
            if (isDuplicateOrderError(errorMsg)) {
                return new SubmissionOutcome.Unknown(Optional.of(httpStatus), errorMsg, Optional.empty());
            }
            return new SubmissionOutcome.Rejected(
                    httpStatus, errorMsg != null ? errorMsg : "success=false", isRetryableError(errorMsg));
        }
        if (errorMsg != null) {
            return new SubmissionOutcome.Unknown(Optional.of(httpStatus), errorMsg, Optional.empty());
        }
        String orderId = blankToNull(node.path("orderID").asText(null));
        if (orderId == null) {
            return new SubmissionOutcome.Unknown(
                    Optional.of(httpStatus), "success without an order id", Optional.empty());
        }
        String status = blankToNull(node.path("status").asText(null));
        if (status == null) {
            return new SubmissionOutcome.Unknown(
                    Optional.of(httpStatus), "success without an order status", Optional.empty());
        }

        // clob-openapi.yaml SendOrderResponse: transactionsHashes is documented on a match.
        return new SubmissionOutcome.Accepted(orderId, status, texts(node, "tradeIDs"),
                texts(node, "transactionsHashes"), textField(node, "makingAmount"),
                textField(node, "takingAmount"));
    }

    /** clob-openapi.yaml SendOrderResponse: an object whose required {@code success} is a boolean. */
    private static boolean isOrderResponse(JsonNode node) {
        return node.isObject() && node.path("success").isBoolean();
    }

    @Override
    public BatchSubmissionOutcome submitBatch(List<BatchItem> items) {
        for (BatchItem item : items) {
            if (item.order().asset() instanceof PositionId) {
                throw new IllegalArgumentException(
                        "V3 Combo orders route through the RFQ Builder Gateway, not POST /orders");
            }
        }
        String body = batchWireBody(items);
        ApiCredentials credentials = items.get(0).placement().credentials();
        String address = items.get(0).order().signer();
        HttpOutcome outcome;
        try {
            outcome = runtime.post(config.clobHost(), ORDERS_PATH,
                    l2Headers(credentials, address, "POST", ORDERS_PATH, body), body);
        } catch (IOException e) {
            return new BatchSubmissionOutcome.Indeterminate(transportMessage(e), Optional.of(e));
        }
        if (!outcome.successful()) {
            return new BatchSubmissionOutcome.Indeterminate(
                    "HTTP " + outcome.status() + ": " + errorMessage(outcome.body()), Optional.empty());
        }
        JsonNode array = tryParse(outcome.body());
        if (array == null || !array.isArray() || array.size() != items.size()) {
            return new BatchSubmissionOutcome.Indeterminate(
                    "batch response did not carry one result per submitted order", Optional.empty());
        }
        // One malformed element makes the whole batch unattributable: no per-item guess is invented.
        for (int i = 0; i < array.size(); i++) {
            if (!isOrderResponse(array.get(i))) {
                return new BatchSubmissionOutcome.Indeterminate("batch result " + i
                        + " is not a documented order object", Optional.empty());
            }
        }
        List<SubmissionOutcome> perItem = new ArrayList<>();
        array.forEach(node -> perItem.add(classifyOrderNode(node, outcome.status())));
        return new BatchSubmissionOutcome.Completed(perItem);
    }

    @Override
    public CancellationOutcome cancel(ApiCredentials credentials, String address, List<String> orderIds)
            throws IOException {
        String body;
        try {
            body = json.writeValueAsString(orderIds);
        } catch (IOException e) {
            throw new IllegalStateException("could not serialize order ids", e);
        }
        HttpOutcome outcome = runtime.delete(config.clobHost(), ORDERS_PATH,
                l2Headers(credentials, address, "DELETE", ORDERS_PATH, body), body);
        if (!outcome.successful()) {
            throw new IOException("could not cancel orders: HTTP " + outcome.status()
                    + " " + errorMessage(outcome.body()));
        }
        JsonNode node = tryParse(outcome.body());
        List<String> canceled = new ArrayList<>();
        if (node != null) {
            node.path("canceled").forEach(c -> canceled.add(c.asText()));
        }
        Map<String, String> notCanceled = new LinkedHashMap<>();
        if (node != null && node.has("not_canceled")) {
            node.path("not_canceled").fields()
                    .forEachRemaining(e -> notCanceled.put(e.getKey(), e.getValue().asText()));
        }
        // Any requested ID the server did not confirm is not-canceled, even without a server reason.
        Set<String> confirmed = new LinkedHashSet<>(canceled);
        for (String id : orderIds) {
            if (!confirmed.contains(id)) {
                notCanceled.putIfAbsent(id, "not confirmed canceled");
            }
        }
        return new CancellationOutcome(canceled, notCanceled);
    }

    private String batchWireBody(List<BatchItem> items) {
        List<Map<String, Object>> payloads = new ArrayList<>();
        for (BatchItem item : items) {
            payloads.add(orderPayload(item.order(), item.placement()));
        }
        try {
            return json.writeValueAsString(payloads);
        } catch (IOException e) {
            throw new IllegalStateException("could not serialize the batch payload", e);
        }
    }

    private String wireBody(SignedOrder order, OrderPlacement placement) {
        try {
            return json.writeValueAsString(orderPayload(order, placement));
        } catch (IOException e) {
            throw new IllegalStateException("could not serialize the order payload", e);
        }
    }

    private static Map<String, Object> orderPayload(SignedOrder order, OrderPlacement placement) {
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("salt", order.salt());
        wire.put("maker", order.maker());
        wire.put("signer", order.signer());
        wire.put("tokenId", order.asset().value());
        wire.put("makerAmount", String.valueOf(order.makerAmount()));
        wire.put("takerAmount", String.valueOf(order.takerAmount()));
        wire.put("side", order.side().name());
        wire.put("expiration", String.valueOf(placement.expirationSeconds()));
        wire.put("signatureType", order.signatureType());
        wire.put("timestamp", String.valueOf(order.timestamp()));
        wire.put("metadata", order.metadata());
        wire.put("builder", order.builder());
        wire.put("signature", order.signature());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("order", wire);
        payload.put("owner", placement.credentials().key());
        payload.put("orderType", placement.orderType().name());
        payload.put("deferExec", false);
        if (placement.orderType() == com.polymarket.trading.OrderType.GTC
                || placement.orderType() == com.polymarket.trading.OrderType.GTD) {
            payload.put("postOnly", placement.postOnly());
        }
        return payload;
    }

    private Map<String, String> l2Headers(ApiCredentials credentials, String address, String method,
            String path, String body) {
        return L2Attestation.headers(credentials, address, clock.instant().getEpochSecond(), method,
                path, body);
    }

    private JsonNode tryParse(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            return runtime.parse(body);
        } catch (IOException notJson) {
            return null;
        }
    }

    private String errorMessage(String body) {
        JsonNode node = tryParse(body);
        if (node != null) {
            String errorMsg = blankToNull(node.path("errorMsg").asText(null));
            if (errorMsg != null) return errorMsg;
            String error = blankToNull(node.path("error").asText(null));
            if (error != null) return error;
        }
        return body != null && !body.isBlank() ? body : "no response body";
    }

    private static String transportMessage(IOException e) {
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }

    private static boolean isDuplicateOrderError(String message) {
        return message != null
                && message.toLowerCase(Locale.ROOT).contains(DUPLICATE_ORDER_MARKER);
    }

    private static boolean isRetryableError(String message) {
        if (message == null) return false;
        String normalized = message.toLowerCase(Locale.ROOT);
        return RETRYABLE_ERRORS.stream().anyMatch(normalized::contains);
    }

    private static List<String> texts(JsonNode node, String field) {
        List<String> values = new ArrayList<>();
        node.path(field).forEach(v -> values.add(v.asText()));
        return values;
    }

    private static Optional<String> textField(JsonNode node, String field) {
        return Optional.ofNullable(blankToNull(node.path(field).asText(null)));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
