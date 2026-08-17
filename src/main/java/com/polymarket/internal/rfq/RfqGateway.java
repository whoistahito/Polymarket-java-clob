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
import com.polymarket.rfq.RfqDirectory;
import com.polymarket.rfq.RfqOutcome;
import com.polymarket.rfq.RfqRequest;
import com.polymarket.rfq.RfqStatus;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transport for the Builder Gateway requester flow. Ground truth:
 * {@code src/test/resources/protocol/builder-gateway.json}. The gateway host is issued per
 * builder onboarding (not a fixed Polymarket host), so it is supplied explicitly here rather
 * than living in {@code PolymarketConfig}.
 */
public final class RfqGateway implements RfqDirectory {

    private static final String REQUESTS_PATH = "/v1/builder/rfq/requests";

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
                accountCredentials, identity.signer(), timestamp, "POST", REQUESTS_PATH, body));
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
        return classify(outcome, rfqId);
    }

    private String requestBody(RfqRequest request, SigningIdentity identity) {
        Map<String, Object> size = new LinkedHashMap<>();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("signer_address", identity.signer());
        body.put("maker_address", identity.maker());
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
        if (status.isNonTerminal()) {
            return new RfqOutcome.Waiting(rfqId, status);
        }
        // CONFIRMED/FILLED only happen after acceptance (issue #26); reaching them here is
        // outside this flow's vocabulary, so the raw value is kept rather than guessed at.
        return new RfqOutcome.Unknown(rfqId, status.raw());
    }

    private RfqOutcome quoted(JsonNode node, String rfqId) {
        JsonNode quote = node.has("quote") ? node.path("quote") : node;
        String quoteId = textOrNull(quote, "quote_id");
        if (quoteId == null) {
            return new RfqOutcome.Waiting(rfqId, new RfqStatus("AWAITING_REQUESTER_ACCEPTANCE"));
        }
        List<PositionId> legs = new ArrayList<>();
        node.path("leg_position_ids").forEach(l -> legs.add(new PositionId(l.asText())));
        return new RfqOutcome.Quoted(rfqId, quoteId, legs,
                Long.parseLong(quote.path("maker_amount_e6").asText("0")),
                Long.parseLong(quote.path("taker_amount_e6").asText("0")),
                Instant.ofEpochMilli(quote.path("expires_at").asLong(0)),
                quote.path("builder_code").asText(""));
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
