package com.polymarket.internal.rfq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.SigningIdentity;
import com.polymarket.builders.BuilderCredentials;
import com.polymarket.internal.authentication.L2Attestation;
import com.polymarket.internal.http.HttpOutcome;
import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.markets.PositionId;
import com.polymarket.rfq.QuoteAmounts;
import com.polymarket.rfq.RfqDirectory;
import com.polymarket.rfq.RfqOutcome;
import com.polymarket.rfq.RfqRequest;
import com.polymarket.rfq.RfqStatus;
import com.polymarket.trading.Side;
import com.polymarket.trading.SignedOrder;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Transport for the Builder Gateway requester flow. Ground truth:
 * {@code src/test/resources/protocol/builder-gateway.json}. The gateway host is issued per
 * builder onboarding (not a fixed Polymarket host), so it is supplied explicitly here rather
 * than living in {@code PolymarketConfig}.
 */
public final class RfqGateway implements RfqDirectory {

    private static final String REQUESTS_PATH = "/v1/builder/rfq/requests";
    private static final String ACCEPT_SUFFIX = "/accept";
    private static final int DEPOSIT_WALLET_SIGNATURE_TYPE = 3;
    private static final int STATE_CONFLICT = 409;

    private final URI gatewayHost;
    private final HttpRuntime runtime;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();

    public RfqGateway(URI gatewayHost, HttpRuntime runtime, Clock clock) {
        this.gatewayHost = gatewayHost;
        this.runtime = runtime;
        this.clock = clock;
    }

    @Override
    public RfqOutcome request(RfqRequest request, SigningIdentity identity,
            ApiCredentials accountCredentials, BuilderCredentials builderCredentials) throws IOException {
        String body = requestBody(request, identity);
        Map<String, String> headers = new LinkedHashMap<>();
        long timestamp = clock.instant().getEpochSecond();
        headers.putAll(L2Attestation.headers(
                accountCredentials, identity.accountSigner(), timestamp, "POST", REQUESTS_PATH, body));
        headers.putAll(builderHeaders(builderCredentials, timestamp, "POST", REQUESTS_PATH, body));

        HttpOutcome outcome = runtime.post(gatewayHost, REQUESTS_PATH, headers, body);
        return classify(outcome, null);
    }

    @Override
    public RfqOutcome status(String rfqId, ApiCredentials accountCredentials, String address)
            throws IOException {
        String path = REQUESTS_PATH + "/" + rfqId;
        long timestamp = clock.instant().getEpochSecond();
        Map<String, String> headers = L2Attestation.headers(
                accountCredentials, address, timestamp, "GET", path, null);

        HttpOutcome outcome = runtime.get(gatewayHost, path, headers);
        // Official: a status read before acceptance is the gateway's documented 409.
        if (outcome.status() == STATE_CONFLICT) {
            return new RfqOutcome.NotYetAccepted(rfqId);
        }
        return classify(outcome, rfqId);
    }

    @Override
    public RfqOutcome accept(String rfqId, String quoteId, SignedOrder signedOrder,
            SigningIdentity identity, ApiCredentials accountCredentials,
            BuilderCredentials builderCredentials) {
        String path = REQUESTS_PATH + "/" + rfqId + ACCEPT_SUFFIX;
        String body = acceptBody(quoteId, signedOrder);
        long timestamp = clock.instant().getEpochSecond();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.putAll(L2Attestation.headers(
                accountCredentials, identity.accountSigner(), timestamp, "POST", path, body));
        headers.putAll(builderHeaders(builderCredentials, timestamp, "POST", path, body));

        try {
            HttpOutcome outcome = runtime.post(gatewayHost, path, headers, body);
            return classify(outcome, rfqId);
        } catch (IOException transportFailure) {
            // Never replayed and never thrown: the acceptance may or may not have landed, so
            // the caller polls status() with the same durable rfqId instead of re-accepting.
            return new RfqOutcome.Unknown(rfqId,
                    "transport failure: " + transportFailure.getMessage());
        }
    }

    private String acceptBody(String quoteId, SignedOrder signedOrder) {
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("salt", signedOrder.salt());
        order.put("maker", signedOrder.maker());
        order.put("signer", signedOrder.signer());
        order.put("tokenId", signedOrder.asset().value());
        order.put("makerAmount", String.valueOf(signedOrder.makerAmount()));
        order.put("takerAmount", String.valueOf(signedOrder.takerAmount()));
        order.put("side", signedOrder.side().wireValue());
        order.put("signatureType", signedOrder.signatureType());
        order.put("timestamp", String.valueOf(signedOrder.timestamp()));
        order.put("metadata", signedOrder.metadata());
        order.put("builder", signedOrder.builder());
        order.put("signature", signedOrder.signature());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("quote_id", quoteId);
        body.put("signed_order", order);
        try {
            return json.writeValueAsString(body);
        } catch (IOException e) {
            throw new IllegalStateException("could not serialize the RFQ acceptance", e);
        }
    }

    private String requestBody(RfqRequest request, SigningIdentity identity) {
        Map<String, Object> size = new LinkedHashMap<>();
        Map<String, Object> body = new LinkedHashMap<>();
        // Only signature type 3 collapses signer_address onto the Trading Wallet;
        // POLY_ADDRESS stays the Account Signer for every type (builder-gateway.json).
        body.put("signer_address", identity.signatureType() == DEPOSIT_WALLET_SIGNATURE_TYPE
                ? identity.tradingWallet() : identity.accountSigner());
        body.put("maker_address", identity.tradingWallet());
        body.put("signature_type", identity.signatureType());
        List<String> legs = new ArrayList<>();
        request.legs().forEach(leg -> legs.add(leg.value()));
        body.put("leg_position_ids", legs);
        body.put("side", "YES");
        if (request instanceof RfqRequest.Buy buy) {
            body.put("direction", "BUY");
            size.put("unit", "notional");
            size.put("value_e6", String.valueOf(buy.notional().baseUnits()));
        } else if (request instanceof RfqRequest.Sell sell) {
            body.put("direction", "SELL");
            size.put("unit", "shares");
            size.put("value_e6", String.valueOf(sell.shares().baseUnits()));
        }
        body.put("requested_size", size);
        try {
            return json.writeValueAsString(body);
        } catch (IOException e) {
            throw new IllegalStateException("could not serialize the RFQ request", e);
        }
    }

    private static Map<String, String> builderHeaders(BuilderCredentials credentials,
            long timestampSeconds, String method, String path, String body) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("POLY_BUILDER_API_KEY", credentials.key());
        headers.put("POLY_BUILDER_PASSPHRASE", credentials.passphrase());
        headers.put("POLY_BUILDER_TIMESTAMP", String.valueOf(timestampSeconds));
        headers.put("POLY_BUILDER_SIGNATURE",
                L2Attestation.sign(credentials.secret(), timestampSeconds, method, path, body));
        return headers;
    }

    /**
     * {@code fallbackRfqId} is used when the body carries none (e.g. an unreadable body on a
     * status read, where the caller already knows the ID it asked about).
     */
    private RfqOutcome classify(HttpOutcome outcome, String fallbackRfqId) throws IOException {
        JsonNode node = tryParse(outcome.body());
        if (node == null) {
            if (fallbackRfqId != null) {
                return new RfqOutcome.Unknown(fallbackRfqId, "unreadable response body");
            }
            throw new IOException("could not read RFQ response: HTTP " + outcome.status());
        }
        String rfqId = textOrNull(node, "rfq_id");
        if (rfqId == null) rfqId = fallbackRfqId;
        if (rfqId == null) {
            throw new IOException("RFQ response carried no rfq_id");
        }
        RfqStatus status = new RfqStatus(node.path("status").asText(""));

        if (status.is(RfqStatus.Known.FAILED)) {
            return new RfqOutcome.Failed(rfqId, errorReason(node));
        }
        if (status.is(RfqStatus.Known.EXPIRED)) {
            return new RfqOutcome.Expired(rfqId);
        }
        if (status.is(RfqStatus.Known.CANCELED)) {
            return new RfqOutcome.Canceled(rfqId);
        }
        if (status.is(RfqStatus.Known.AWAITING_REQUESTER_ACCEPTANCE)) {
            return quoted(node, rfqId);
        }
        if (status.is(RfqStatus.Known.CONFIRMED) || status.is(RfqStatus.Known.FILLED)) {
            return new RfqOutcome.Confirmed(rfqId, status.raw(), takerOrderHash(node));
        }
        if (status.isNonTerminal()) {
            return new RfqOutcome.Waiting(rfqId, status, takerOrderHash(node));
        }
        return new RfqOutcome.Unknown(rfqId, status.raw());
    }

    /**
     * Official shape: expires_at and builder_code are TOP LEVEL, the Combo position and legs
     * live under request, and only the six pinned quote fields live under quote.
     */
    private RfqOutcome quoted(JsonNode node, String rfqId) {
        JsonNode quote = node.path("quote");
        JsonNode request = node.path("request");
        String quoteId = textOrNull(quote, "quote_id");
        String comboPositionId = textOrNull(request, "yes_position_id");
        if (quoteId == null || comboPositionId == null) {
            return new RfqOutcome.Waiting(rfqId,
                    new RfqStatus("AWAITING_REQUESTER_ACCEPTANCE"), Optional.empty());
        }
        List<PositionId> legs = new ArrayList<>();
        request.path("leg_position_ids").forEach(l -> legs.add(new PositionId(l.asText())));
        QuoteAmounts amounts = new QuoteAmounts(
                baseUnits(quote, "blended_price_e6"),
                baseUnits(quote, "maker_amount_e6"),
                baseUnits(quote, "taker_amount_e6"),
                baseUnits(quote, "total_required_e6"),
                baseUnits(quote, "net_receive_e6"));
        return new RfqOutcome.Quoted(rfqId, quoteId, direction(request), new PositionId(comboPositionId),
                legs, amounts, Instant.ofEpochMilli(node.path("expires_at").asLong(0)),
                node.path("builder_code").asText(""));
    }

    /** SELL only when the gateway says so, so an unreadable direction never flips a BUY. */
    private static Side direction(JsonNode request) {
        return "SELL".equalsIgnoreCase(request.path("direction").asText("")) ? Side.SELL : Side.BUY;
    }

    private static long baseUnits(JsonNode node, String field) {
        String value = textOrNull(node, field);
        return value == null ? 0L : Long.parseLong(value);
    }

    /** Present on an acceptance response; a safe retry may omit it, so it stays optional. */
    private static Optional<String> takerOrderHash(JsonNode node) {
        return Optional.ofNullable(textOrNull(node, "taker_order_hash"));
    }

    private static String errorReason(JsonNode node) {
        JsonNode error = node.path("error");
        String message = textOrNull(error, "message");
        if (message != null) return message;
        String flatError = error.isTextual() ? error.asText() : null;
        if (flatError != null && !flatError.isBlank()) return flatError;
        String topLevel = textOrNull(node, "error_message");
        return topLevel != null ? topLevel : "RFQ failed";
    }

    private JsonNode tryParse(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            return runtime.parse(body);
        } catch (IOException notJson) {
            return null;
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }
}
