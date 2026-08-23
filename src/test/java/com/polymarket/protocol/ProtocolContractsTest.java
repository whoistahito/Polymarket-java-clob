package com.polymarket.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

/**
 * Pins the official 2026 protocol contracts. Expected values come from Polymarket's published
 * documentation and an independent signer, never from this SDK's production code.
 */
@DisplayName("Official 2026 protocol contracts and signing vectors")
class ProtocolContractsTest {

    /** The date every fixture's sources were last re-read; see docs/protocol/SOURCES.md. */
    private static final String REVIEWED_ON = "2026-08-23";

    // The production hosts the OpenAPI `servers` blocks declare; every other URL must be a doc page.
    private static final List<String> OFFICIAL_API_HOSTS =
            List.of("https://clob.polymarket.com", "https://data-api.polymarket.com");

    // Published verbatim by Polymarket in the "Sign the Order" examples for both exchanges:
    // docs.polymarket.com/trading/place-orders and /trading/combos/market-makers.
    private static final String OFFICIAL_ORDER_ENCODE_TYPE =
            "Order(uint256 salt,address maker,address signer,uint256 tokenId,uint256 makerAmount,"
                    + "uint256 takerAmount,uint8 side,uint8 signatureType,uint256 timestamp,"
                    + "bytes32 metadata,bytes32 builder)";

    private static final String OFFICIAL_TYPED_DATA_SIGN_ENCODE_TYPE =
            "TypedDataSign(Order contents,string name,string version,uint256 chainId,"
                    + "address verifyingContract,bytes32 salt)"
                    + OFFICIAL_ORDER_ENCODE_TYPE;

    private static final JsonNode VECTORS = load("/protocol/signing-vectors.json");
    private static final JsonNode CONSTRAINTS = load("/protocol/constraints.json");
    private static final JsonNode GATEWAY = load("/protocol/builder-gateway.json");
    private static final JsonNode FEES = load("/protocol/fees.json");
    private static final JsonNode TRADES = load("/protocol/trades.json");
    private static final JsonNode BUILDER_TRADES = load("/protocol/builder-trades.json");
    private static final JsonNode HEARTBEAT = load("/protocol/heartbeat.json");

    private static JsonNode load(String resource) {
        try (InputStream in = ProtocolContractsTest.class.getResourceAsStream(resource)) {
            return new ObjectMapper().readTree(in);
        } catch (Exception e) {
            throw new IllegalStateException("missing protocol fixture " + resource, e);
        }
    }

    static List<JsonNode> vectors() {
        List<JsonNode> all = new ArrayList<>();
        VECTORS.get("vectors").forEach(all::add);
        return all;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vectorIds")
    @DisplayName("TC-PC-001: each vector encodes the official Order type verbatim")
    void vectorsEncodeTheOfficialOrderType(String id) {
        JsonNode v = vector(id);
        String expected = "TypedDataSign".equals(v.get("primaryType").asText())
                ? OFFICIAL_TYPED_DATA_SIGN_ENCODE_TYPE
                : OFFICIAL_ORDER_ENCODE_TYPE;
        assertEquals(expected, v.get("encodeType").asText());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vectorIds")
    @DisplayName("TC-PC-002: each vector's signature recovers to its stated signer over its digest")
    void vectorSignaturesRecoverToTheStatedSigner(String id) {
        JsonNode v = vector(id);
        byte[] digest = Numeric.hexStringToByteArray(v.get("digest").asText());
        byte[] sig = Numeric.hexStringToByteArray(v.get("signature").asText());
        assertEquals(65, sig.length, "EIP-712 signatures are 65 bytes");

        byte header = sig[64];
        Sign.SignatureData data = new Sign.SignatureData(
                (byte) (header < 27 ? header + 27 : header),
                java.util.Arrays.copyOfRange(sig, 0, 32),
                java.util.Arrays.copyOfRange(sig, 32, 64));

        BigInteger publicKey;
        try {
            publicKey = Sign.signedMessageHashToKey(digest, data);
        } catch (Exception e) {
            throw new AssertionError("could not recover signer for vector " + id, e);
        }
        assertEquals(v.get("signerAddress").asText().toLowerCase(Locale.ROOT),
                Numeric.prependHexPrefix(Keys.getAddress(publicKey)));
    }

    @Test
    @DisplayName("TC-PC-003: V2 and V3 sign under different domains and exchange contracts")
    void v2AndV3AreDistinctDomains() {
        JsonNode contracts = CONSTRAINTS.get("contracts");
        JsonNode v2 = vector("v2-eoa").get("domain");
        JsonNode v3 = vector("v3-eoa").get("domain");

        assertEquals("2", v2.get("version").asText());
        assertEquals("3", v3.get("version").asText());
        assertEquals(contracts.get("ctfExchangeV2").asText(), v2.get("verifyingContract").asText());
        assertEquals(contracts.get("combosExchangeV3").asText(), v3.get("verifyingContract").asText());
        assertEquals(137, v2.get("chainId").asInt());
        assertEquals(137, v3.get("chainId").asInt());
        assertNotEquals(vector("v2-eoa").get("domainSeparator").asText(),
                vector("v3-eoa").get("domainSeparator").asText());
    }

    @Test
    @DisplayName("TC-PC-004: Deposit Wallet vectors wrap the order for ERC-7739 validation")
    void depositWalletVectorsAreWrapped() {
        for (String id : List.of("v2-deposit-wallet", "v3-deposit-wallet")) {
            JsonNode message = vector(id).get("message");
            assertEquals("DepositWallet", message.get("name").asText(), id);
            assertEquals("1", message.get("version").asText(), id);
            assertEquals(3, message.get("contents").get("signatureType").asInt(), id);
            assertNotEquals(vector(id).get("domain").get("verifyingContract").asText(),
                    message.get("verifyingContract").asText(),
                    id + ": the wrapper verifies against the wallet, not the exchange");
        }
        assertTrue(CONSTRAINTS.get("signatureTypes").get("depositWalletSignatureIsErc7739Wrapped").asBoolean());
    }

    @Test
    @DisplayName("TC-PC-005: V2 and V3 order timestamps keep their conflicting official units")
    void timestampUnitsStaySeparate() {
        JsonNode struct = CONSTRAINTS.get("orderStruct");
        assertEquals("unix-milliseconds", struct.get("v2TimestampUnit").asText());
        assertEquals("unix-seconds", struct.get("v3TimestampUnit").asText());
        assertFalse(struct.get("expirationIsSignedField").asBoolean(),
                "expiration rides the wire body but is not signed");
        List<String> fields = new ArrayList<>();
        vector("v2-eoa").get("types").get("Order")
                .forEach(f -> fields.add(f.get("name").asText()));
        for (String removed : List.of("nonce", "feeRateBps", "taker")) {
            assertFalse(fields.contains(removed),
                    removed + " was removed in V2 and must not appear in the signed struct");
        }
        assertTrue(fields.containsAll(List.of("timestamp", "metadata", "builder")));
    }

    @Test
    @DisplayName("TC-PC-006: Gamma notional never substitutes for the CLOB minimum-share rule")
    void minimumSizeSourcesStaySeparate() {
        JsonNode clob = CONSTRAINTS.get("minimumSize").get("clobSigningRule");
        JsonNode gamma = CONSTRAINTS.get("minimumSize").get("gammaDiscoveryMetadata");

        assertTrue(clob.get("authoritativeForSigning").asBoolean());
        assertFalse(gamma.get("authoritativeForSigning").asBoolean());
        assertNotEquals(clob.get("unit").asText(), gamma.get("unit").asText());
        assertNotEquals(clob.get("field").asText(), gamma.get("field").asText());
    }

    @Test
    @DisplayName("TC-PC-007: the Builder Gateway requester flow is pinned to three endpoints")
    void builderGatewayRequesterFlowIsPinned() {
        List<String> routes = new ArrayList<>();
        GATEWAY.get("endpoints").forEach(e -> routes.add(
                e.get("method").asText() + " " + e.get("path").asText()
                        + (e.get("builderAuth").asBoolean() ? " +builder" : "")));

        assertEquals(List.of(
                "POST /v1/builder/rfq/requests +builder",
                "POST /v1/builder/rfq/requests/{rfq_id}/accept +builder",
                "GET /v1/builder/rfq/requests/{rfq_id}"), routes);

        JsonNode statuses = GATEWAY.get("statuses");
        assertEquals(List.of("FAILED", "EXPIRED", "CANCELED"),
                textList(statuses.get("terminalWithoutFill")));
        assertEquals(List.of("CONFIRMED", "FILLED"), textList(statuses.get("success")));
    }

    @Test
    @DisplayName("TC-PC-008: settlement resolves through trade IDs, not inline transaction hashes")
    void settlementIsReconciledThroughTradeIds() {
        JsonNode settlement = CONSTRAINTS.get("settlement");
        assertFalse(settlement.get("postOrderReturnsTransactionHashes").asBoolean());
        assertTrue(settlement.get("postOrderReturnsTradeIds").asBoolean());
    }

    @Test
    @DisplayName("TC-PC-009: official batch and GTD limits are recorded as testable numbers")
    void officialLimitsArePinned() {
        assertEquals(15, CONSTRAINTS.get("batchLimits").get("postOrdersMax").asInt());
        assertEquals(1000, CONSTRAINTS.get("batchLimits").get("cancelOrdersMax").asInt());

        JsonNode gtd = CONSTRAINTS.get("gtd");
        assertEquals(60, gtd.get("securityThresholdSeconds").asInt());
        assertEquals(180, gtd.get("minimumFutureSeconds").asInt());
        assertEquals("unix-seconds", gtd.get("expirationUnit").asText());
    }

    @Test
    @DisplayName("TC-PC-010: the taker fee follows the official price-curve formula, never a flat rate")
    void takerFeeFollowsTheOfficialPriceCurve() {
        assertEquals("fee = C x feeRate x p x (1 - p)", FEES.get("formula").asText());
        assertEquals("shares traded", FEES.get("variables").get("C").asText());
        assertEquals("price of the shares", FEES.get("variables").get("p").asText());
        assertTrue(FEES.get("takerOnly").asBoolean(), "makers are never charged fees");
        assertTrue(FEES.get("appliedAtMatchTime").asBoolean(),
                "fees are set at match time and are not carried on the order");

        FEES.get("categoryRates").fields().forEachRemaining(entry ->
                assertEquals(BigDecimal.ZERO,
                        new BigDecimal(entry.getValue().get("makerRate").asText()).stripTrailingZeros(),
                        entry.getKey() + " charges makers"));
    }

    @Test
    @DisplayName("TC-PC-011: fees round to five decimals with a 0.00001 USDC floor")
    void feePrecisionIsPinned() {
        JsonNode precision = FEES.get("precision");
        assertEquals(5, precision.get("decimalPlaces").asInt());
        assertEquals(new BigDecimal("0.00001"),
                new BigDecimal(precision.get("smallestChargeableFee").asText()));
        assertTrue(precision.get("belowTheFloorRoundsToZero").asBoolean());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("feeExampleIds")
    @DisplayName("TC-PC-012: each derived fee example reproduces Polymarket's published table")
    void derivedFeeExamplesMatchThePublishedTable(String id) {
        JsonNode example = feeExample(id);
        BigDecimal shares = new BigDecimal(example.get("shares").asText());
        BigDecimal rate = new BigDecimal(example.get("feeRate").asText());
        BigDecimal price = new BigDecimal(example.get("price").asText());
        BigDecimal exact = new BigDecimal(example.get("exactFeeUsdc").asText());

        assertEquals(0, exact.compareTo(
                        shares.multiply(rate).multiply(price).multiply(BigDecimal.ONE.subtract(price))
                                .setScale(exact.scale(), RoundingMode.HALF_UP)),
                id + ": the derived fee must satisfy C x feeRate x p x (1 - p)");

        assertEquals(new BigDecimal(example.get("feeAtFivePlaces").asText()),
                exact.setScale(5, RoundingMode.HALF_UP),
                id + ": the charged fee is the exact fee at five decimal places");

        if (example.has("publishedUsdc")) {
            assertEquals(new BigDecimal(example.get("publishedUsdc").asText()),
                    exact.setScale(2, RoundingMode.HALF_UP),
                    id + ": disagrees with the published fee table");
        }
    }

    @Test
    @DisplayName("TC-PC-013: the fee in USDC is symmetric around a 0.50 price")
    void feeIsSymmetricAroundFiftyPercent() {
        assertTrue(FEES.get("symmetricAroundHalf").asBoolean());
        assertEquals(new BigDecimal(feeExample("crypto-100-shares-0.30").get("exactFeeUsdc").asText()),
                new BigDecimal(feeExample("crypto-100-shares-0.70").get("exactFeeUsdc").asText()));
    }

    @Test
    @DisplayName("TC-PC-014: CLOB trades arrive in a four-field page envelope, never a bare array")
    void clobTradesArriveInAPageEnvelope() {
        JsonNode page = TRADES.get("clobTradePage");
        assertEquals(List.of("limit", "next_cursor", "count", "data"),
                textList(page.get("envelopeFields")));
        assertEquals(List.of("limit", "next_cursor", "count", "data"),
                textList(page.get("requiredEnvelopeFields")));
        assertEquals("data", page.get("rowsField").asText());
        assertEquals("MA==", page.get("openingCursor").asText());
        assertEquals("LTE=", page.get("endOfPagesCursor").asText());
        assertEquals("query", page.get("cursorPlacement").asText());
        assertFalse(page.get("responseIsBareArray").asBoolean(),
                "the CLOB page is an object; iterating it as an array reads the scalars, not the rows");
    }

    @Test
    @DisplayName("TC-PC-015: the trade-status vocabulary is exactly five TRADE_STATUS_ values")
    void tradeStatusVocabularyIsComplete() {
        JsonNode status = TRADES.get("tradeStatus");
        assertEquals(List.of(
                "TRADE_STATUS_CONFIRMED",
                "TRADE_STATUS_FAILED",
                "TRADE_STATUS_RETRYING",
                "TRADE_STATUS_MATCHED",
                "TRADE_STATUS_MINED"), textList(status.get("wireValues")));
        assertEquals("TRADE_STATUS_", status.get("wirePrefix").asText());
        assertEquals(List.of("CONFIRMED", "FAILED"), textList(status.get("terminal")));
        assertEquals(List.of("MATCHED", "MINED", "RETRYING"), textList(status.get("nonTerminal")));
    }

    @Test
    @DisplayName("TC-PC-016: GET /data/trades requires maker_address and is level-2 authenticated")
    void clobTradeReadRequiresTheMakerAddress() {
        JsonNode endpoint = TRADES.get("clobTradeRead");
        assertEquals("GET", endpoint.get("method").asText());
        assertEquals("/data/trades", endpoint.get("path").asText());
        assertEquals(List.of("maker_address"), textList(endpoint.get("requiredQuery")));
        assertTrue(textList(endpoint.get("optionalQuery")).containsAll(
                List.of("id", "market", "asset_id", "before", "after", "next_cursor")));
        assertEquals(List.of("POLY_API_KEY", "POLY_ADDRESS", "POLY_SIGNATURE", "POLY_PASSPHRASE",
                "POLY_TIMESTAMP"), textList(endpoint.get("authHeaders")));
    }

    @Test
    @DisplayName("TC-PC-017: the Data API trade feed is a different contract from the CLOB trade page")
    void dataApiTradeFeedIsNotTheClobPage() {
        JsonNode data = TRADES.get("dataApiTradeFeed");
        assertNotEquals(TRADES.get("clobTradeRead").get("host").asText(), data.get("host").asText());
        assertTrue(data.get("responseIsBareArray").asBoolean(),
                "the Data API returns a bare JSON array with no envelope");
        assertEquals("offset", data.get("paginationStyle").asText());
        assertEquals(10000, data.get("maxLimit").asInt());
        assertEquals(10000, data.get("maxOffset").asInt());
        assertFalse(data.get("offsetOverCapIsClamped").asBoolean(),
                "an offset past the cap is rejected with 400, never silently clamped");
    }

    @Test
    @DisplayName("TC-PC-018: GET /builder/trades is filtered by a required builder_code")
    void builderTradesRequireTheBuilderCode() {
        assertEquals("GET", BUILDER_TRADES.get("method").asText());
        assertEquals("/builder/trades", BUILDER_TRADES.get("path").asText());
        assertEquals(List.of("builder_code"), textList(BUILDER_TRADES.get("requiredQuery")));
        assertEquals("^0x[a-fA-F0-9]{64}$", BUILDER_TRADES.get("builderCodePattern").asText());
        assertEquals(List.of("id", "market", "asset_id", "before", "after", "next_cursor"),
                textList(BUILDER_TRADES.get("optionalQuery")));
    }

    @Test
    @DisplayName("TC-PC-019: builder trades continue through before/after windows and a page cursor")
    void builderTradeContinuationIsPinned() {
        JsonNode continuation = BUILDER_TRADES.get("continuation");
        assertEquals("unix-seconds, as a decimal string", continuation.get("beforeUnit").asText());
        assertEquals("unix-seconds, as a decimal string", continuation.get("afterUnit").asText());
        assertEquals("^\\d+$", continuation.get("beforeAfterPattern").asText());
        assertEquals("next_cursor", continuation.get("cursorParameter").asText());
        assertEquals("LTE=", continuation.get("endOfPagesCursor").asText());

        JsonNode page = BUILDER_TRADES.get("page");
        assertEquals(List.of("limit", "next_cursor", "count", "data"),
                textList(page.get("requiredEnvelopeFields")));
        assertEquals(300, page.get("exampleLimit").asInt());
    }

    @Test
    @DisplayName("TC-PC-020: a builder trade carries a unix match time and ISO creation/update times")
    void builderTradeTimestampUnitsAreDistinct() {
        JsonNode units = BUILDER_TRADES.get("timestampUnits");
        assertEquals("unix-seconds, as a decimal string", units.get("matchTime").asText());
        assertEquals("ISO-8601 date-time", units.get("createdAt").asText());
        assertEquals("ISO-8601 date-time", units.get("updatedAt").asText());
        assertNotEquals(units.get("matchTime").asText(), units.get("createdAt").asText(),
                "one parser cannot read both; a unix match time is not an ISO instant");

        assertEquals("1700000000", BUILDER_TRADES.get("examples").get("matchTime").asText());
        assertEquals("2024-01-01T00:00:00Z", BUILDER_TRADES.get("examples").get("createdAt").asText());
    }

    @Test
    @DisplayName("TC-PC-021: the published Heartbeat is a bodyless POST /heartbeats under level-2 auth")
    void heartbeatIsBodyless() {
        JsonNode current = HEARTBEAT.get("current");
        assertEquals("POST", current.get("method").asText());
        assertEquals("/heartbeats", current.get("path").asText());
        assertFalse(current.get("hasRequestBody").asBoolean(),
                "the published Heartbeat declares no requestBody at all");
        assertEquals("", current.get("requestBody").asText());
        assertEquals("L2", current.get("authLevel").asText());
        assertEquals(List.of("POLY_API_KEY", "POLY_ADDRESS", "POLY_SIGNATURE", "POLY_PASSPHRASE",
                "POLY_TIMESTAMP"), textList(current.get("authHeaders")));
        assertEquals(List.of("status"), textList(current.get("responseFields")));
        assertEquals("ok", current.get("responseExample").get("status").asText());
    }

    @Test
    @DisplayName("TC-PC-022: the Heartbeat is a dead-man switch with no published beat interval")
    void heartbeatIsADeadManSwitchWithNoPublishedInterval() {
        assertTrue(HEARTBEAT.get("cancelsOpenOrdersWhenBeatsStop").asBoolean());
        assertFalse(HEARTBEAT.get("intervalIsPublished").asBoolean(),
                "no official page states a beat interval or a silence timeout");
        assertTrue(HEARTBEAT.get("publishedIntervalSeconds").isNull());
    }

    @Test
    @DisplayName("TC-PC-023: the id-chaining /v1/heartbeats variant is a separate, unlisted contract")
    void versionedHeartbeatIsASeparateContract() {
        JsonNode legacy = HEARTBEAT.get("idChainingVariant");
        assertEquals("/v1/heartbeats", legacy.get("path").asText());
        assertTrue(legacy.get("hasRequestBody").asBoolean());
        assertEquals(List.of("heartbeat_id"), textList(legacy.get("requiredRequestFields")));
        assertFalse(legacy.get("listedInDocumentationIndex").asBoolean(),
                "llms.txt lists only the bodyless POST /heartbeats as the Heartbeat endpoint");
        assertNotEquals(HEARTBEAT.get("current").get("path").asText(), legacy.get("path").asText());
    }

    @Test
    @DisplayName("TC-PC-024: only POLY_ADDRESS carries the Account Signer; every body address is the Trading Wallet")
    void rfqAddressRolesAreSeparated() {
        JsonNode roles = GATEWAY.get("addressRoles");
        assertEquals(List.of("POLY_ADDRESS"), textList(roles.get("accountSigner")));
        assertEquals(List.of(
                "createRequest.signer_address",
                "createRequest.maker_address",
                "acceptRequest.signed_order.maker",
                "acceptRequest.signed_order.signer"), textList(roles.get("tradingWallet")));
        assertTrue(roles.get("signerAddressIsNotTheAccountSigner").asBoolean(),
                "signer_address names the Deposit Wallet, not the EOA that owns the credentials");

        JsonNode byType = roles.get("signerAddressByWalletType");
        assertEquals("trading-wallet", byType.get("3").get("signer_address").asText(),
                "a Deposit Wallet order names the Trading Wallet as signer_address");
        for (String proxyOrSafe : List.of("1", "2")) {
            assertEquals("account-signer", byType.get(proxyOrSafe).get("signer_address").asText(),
                    proxyOrSafe + ": a Proxy or Safe order names the Account Signer as signer_address");
            assertEquals("trading-wallet", byType.get(proxyOrSafe).get("maker_address").asText());
        }
    }

    @Test
    @DisplayName("TC-PC-025: the RFQ acceptance deadline and builder code sit above the quote, not inside it")
    void rfqCreateResponseShapeIsPinned() {
        JsonNode response = GATEWAY.get("createResponse");
        assertEquals(List.of("rfq_id", "status", "expires_at", "builder_code", "request", "quote"),
                textList(response.get("topLevelFields")));
        assertEquals(List.of("quote_id", "blended_price_e6", "maker_amount_e6", "taker_amount_e6",
                "total_required_e6", "net_receive_e6"), textList(response.get("quoteFields")));
        assertEquals(List.of("rfq_id", "maker_address", "requestor_public_id", "leg_position_ids",
                "condition_id", "yes_position_id", "no_position_id", "direction", "side",
                "requested_size", "created_at"), textList(response.get("requestFields")));
        assertEquals("request.yes_position_id", GATEWAY.get("comboPositionIdField").asText());
        assertEquals(List.of("expires_at", "request.created_at"),
                textList(GATEWAY.get("timestampsInMilliseconds")));
    }

    @Test
    @DisplayName("TC-PC-026: acceptance and status responses are distinct, narrow shapes")
    void rfqAcceptanceAndStatusShapesArePinned() {
        assertEquals(List.of("rfq_id", "status", "taker_order_hash"),
                textList(GATEWAY.get("acceptResponse").get("fields")));
        assertEquals(List.of("rfq_id", "status", "tx_hash", "error"),
                textList(GATEWAY.get("statusResponse").get("fields")));
        assertFalse(GATEWAY.get("statusResponse").get("repeatsRequestOrQuote").asBoolean(),
                "the status read returns one status, never the request or quote again");
        assertEquals(409, GATEWAY.get("statusResponse").get("beforeAcceptanceHttpStatus").asInt());
    }

    @Test
    @DisplayName("TC-PC-027: the six-value tick grid and its precision table are officially published")
    void tickGridIsOfficiallyPublished() {
        JsonNode grid = CONSTRAINTS.get("tickGrid");
        assertEquals(List.of("0.1", "0.01", "0.005", "0.0025", "0.001", "0.0001"),
                textList(grid.get("values")));
        assertTrue(grid.get("officiallyPublished").asBoolean(),
                "place-orders now publishes the whole grid; it is no longer this repo's own observation");

        JsonNode precision = grid.get("precision");
        assertEquals(List.of("priceDecimals", "sizeDecimals", "amountDecimals"),
                textList(grid.get("precisionColumns")));
        for (String tick : textList(grid.get("values"))) {
            JsonNode row = precision.get(tick);
            assertEquals(row.get("priceDecimals").asInt() + row.get("sizeDecimals").asInt(),
                    row.get("amountDecimals").asInt(),
                    tick + ": amount decimals are price decimals plus size decimals");
            assertEquals(2, row.get("sizeDecimals").asInt(), tick + ": size is always 2 decimals");
        }
        assertEquals(4, precision.get("0.0025").get("priceDecimals").asInt(),
                "a 0.0025 tick prices to four decimals, not three");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtureNames")
    @DisplayName("TC-PC-028: every protocol fixture records the date its sources were re-read")
    void everyFixtureRecordsItsReviewDate(String name) {
        JsonNode fixture = load("/protocol/" + name);
        assertEquals(REVIEWED_ON, fixture.get("reviewedOn").asText(),
                name + ": refresh the fixture or its review date, never one without the other");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtureNames")
    @DisplayName("TC-PC-029: every fixture cites official Polymarket documentation and nothing else")
    void everyFixtureCitesOfficialDocumentationOnly(String name) {
        List<String> urls = new ArrayList<>();
        collectUrls(load("/protocol/" + name), urls);
        assertFalse(urls.isEmpty(), name + ": carries no source URL");
        for (String url : urls) {
            assertTrue(url.startsWith("https://docs.polymarket.com/") || OFFICIAL_API_HOSTS.contains(url),
                    name + ": " + url + " is neither official documentation nor a documented API host");
        }
    }

    private static void collectUrls(JsonNode node, List<String> urls) {
        if (node.isTextual()) {
            for (String word : node.asText().split("\\s+")) {
                if (word.startsWith("http")) {
                    urls.add(word);
                }
            }
            return;
        }
        node.forEach(child -> collectUrls(child, urls));
    }

    static List<String> fixtureNames() {
        return List.of("signing-vectors.json", "constraints.json", "builder-gateway.json",
                "fees.json", "trades.json", "builder-trades.json", "heartbeat.json");
    }

    static List<String> feeExampleIds() {
        List<String> ids = new ArrayList<>();
        FEES.get("derivedExamples").forEach(e -> ids.add(e.get("id").asText()));
        return ids;
    }

    private static JsonNode feeExample(String id) {
        for (JsonNode example : FEES.get("derivedExamples")) {
            if (id.equals(example.get("id").asText())) {
                return example;
            }
        }
        throw new AssertionError("no fee example " + id);
    }

    static List<String> vectorIds() {
        return vectors().stream().map(v -> v.get("id").asText()).toList();
    }

    private static JsonNode vector(String id) {
        return vectors().stream()
                .filter(v -> id.equals(v.get("id").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no vector " + id));
    }

    private static List<String> textList(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }
}
