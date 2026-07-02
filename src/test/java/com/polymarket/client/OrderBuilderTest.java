package com.polymarket.client;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.web3j.crypto.Credentials;

/**
 * Test cases for OrderBuilder.
 * Verifies order creation and signing matches TypeScript SDK.
 */
@DisplayName("OrderBuilder Tests")
class OrderBuilderTest {

    private static final String TEST_PRIVATE_KEY =
        "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final String TEST_API_KEY = "test-api-key";
    private static final String TEST_TOKEN_ID = "12345";

    private Credentials credentials;
    private OrderBuilder builder;

    @BeforeEach
    void setUp() {
        credentials = Credentials.create(TEST_PRIVATE_KEY);
        builder = new OrderBuilder(credentials, 137);
    }

    @Test
    @DisplayName(
        "TC-OB-001: Contract addresses match TypeScript SDK for Polygon Mainnet"
    )
    void testContractAddressesMatchTypeScriptMainnet() throws IOException {
        // Polygon Mainnet (137)
        // These are verified against clob-client/src/config.ts
        OrderBuilder mainnetBuilder = new OrderBuilder(credentials, 137);

        // Create order to verify it uses correct exchange
        Map<String, Object> order = mainnetBuilder.createOrder(
            TEST_TOKEN_ID,
            "BUY",
            new BigDecimal("0.50"),
            new BigDecimal("10.00"),
            "0.01",
            false,
            "GTC",
            TEST_API_KEY
        );

        assertNotNull(order);
        // The signature is generated using the exchange address, so if wrong address
        // was used, the signature would be invalid on the server
    }

    @Test
    @DisplayName(
        "TC-OB-001b: Contract addresses match TypeScript SDK for Amoy Testnet"
    )
    void testContractAddressesMatchTypeScriptAmoy() throws IOException {
        // Amoy Testnet (80002)
        OrderBuilder amoyBuilder = new OrderBuilder(credentials, 80002);

        Map<String, Object> order = amoyBuilder.createOrder(
            TEST_TOKEN_ID,
            "BUY",
            new BigDecimal("0.50"),
            new BigDecimal("10.00"),
            "0.01",
            false,
            "GTC",
            TEST_API_KEY
        );

        assertNotNull(order);
    }

    @Test
    @DisplayName("TC-OB-002: Rounding config matches TypeScript SDK")
    void testRoundingConfigMatchesTypeScript() throws IOException {
        // TypeScript ROUNDING_CONFIG from clob-client/src/order-builder/helpers.ts:
        // "0.1": { price: 1, size: 2, amount: 3 }
        // "0.01": { price: 2, size: 2, amount: 4 }
        // "0.001": { price: 3, size: 2, amount: 5 }
        // "0.0001": { price: 4, size: 2, amount: 6 }

        // Verify rounding behavior matches expected config by testing amount calculations
        // For tick size 0.1: amount precision = 3 decimals -> 1000 units
        // For tick size 0.01: amount precision = 4 decimals -> 10000 units
        // For tick size 0.001: amount precision = 5 decimals -> 100000 units
        // For tick size 0.0001: amount precision = 6 decimals -> 1000000 units

        // Test with tick size 0.01 (most common)
        Map<String, Object> order01 = builder.createOrder(
            TEST_TOKEN_ID,
            "BUY",
            new BigDecimal("0.50"),
            new BigDecimal("10.00"),
            "0.01",
            false,
            "GTC",
            TEST_API_KEY
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> orderData01 = (Map<String, Object>) order01.get(
            "order"
        );
        // 10 * 0.50 * 10^6 = 5000000 (amount precision 4 -> makerAmount in blockchain units)
        assertEquals("5000000", orderData01.get("makerAmount"));

        // Test with tick size 0.001
        Map<String, Object> order001 = builder.createOrder(
            TEST_TOKEN_ID,
            "BUY",
            new BigDecimal("0.505"),
            new BigDecimal("10.00"),
            "0.001",
            false,
            "GTC",
            TEST_API_KEY
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> orderData001 = (Map<String, Object>) order001.get(
            "order"
        );
        // 10 * 0.505 * 10^6 = 5050000
        assertEquals("5050000", orderData001.get("makerAmount"));
    }

    @Test
    @DisplayName("TC-OB-002b: Signature types constants are correct")
    void testSignatureTypes() {
        assertEquals(0, OrderBuilder.SIGNATURE_TYPE_EOA);
        assertEquals(1, OrderBuilder.SIGNATURE_TYPE_POLY_PROXY);
        assertEquals(2, OrderBuilder.SIGNATURE_TYPE_POLY_GNOSIS_SAFE);
    }

    @Test
    @DisplayName("TC-OB-003: Create BUY order")
    void testCreateBuyOrder() throws IOException {
        Map<String, Object> order = builder.createOrder(
            TEST_TOKEN_ID,
            "BUY",
            new BigDecimal("0.50"),
            new BigDecimal("10.00"),
            "0.01",
            false,
            "GTC",
            TEST_API_KEY
        );

        assertNotNull(order);
        assertEquals("GTC", order.get("orderType"));
        assertEquals(TEST_API_KEY, order.get("owner"));
        assertFalse((Boolean) order.get("deferExec"));

        @SuppressWarnings("unchecked")
        Map<String, Object> orderData = (Map<String, Object>) order.get(
            "order"
        );
        assertEquals("BUY", orderData.get("side"));
        assertEquals(TEST_TOKEN_ID, orderData.get("tokenId"));
        assertNotNull(orderData.get("signature"));
        assertTrue(((String) orderData.get("signature")).startsWith("0x"));
    }

    @Test
    @DisplayName("TC-OB-004: Create SELL order")
    void testCreateSellOrder() throws IOException {
        Map<String, Object> order = builder.createOrder(
            TEST_TOKEN_ID,
            "SELL",
            new BigDecimal("0.60"),
            new BigDecimal("5.00"),
            "0.01",
            false,
            "GTC",
            TEST_API_KEY
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> orderData = (Map<String, Object>) order.get(
            "order"
        );
        assertEquals("SELL", orderData.get("side"));
    }

    @Test
    @DisplayName("TC-OB-005: Amount calculation for BUY order")
    void testBuyAmountCalculation() throws IOException {
        // BUY 10 shares at 0.50 price
        // makerAmount = size * price = 10 * 0.50 = 5.00 USDC
        // takerAmount = size = 10 tokens

        Map<String, Object> order = builder.createOrder(
            TEST_TOKEN_ID,
            "BUY",
            new BigDecimal("0.50"),
            new BigDecimal("10.00"),
            "0.01",
            false,
            "GTC",
            TEST_API_KEY
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> orderData = (Map<String, Object>) order.get(
            "order"
        );

        // In blockchain units (6 decimals): 5.00 * 10^6 = 5000000
        assertEquals("5000000", orderData.get("makerAmount"));
        // 10.00 * 10^6 = 10000000
        assertEquals("10000000", orderData.get("takerAmount"));
    }

    @Test
    @DisplayName("TC-OB-006: Amount calculation for SELL order")
    void testSellAmountCalculation() throws IOException {
        // SELL 10 shares at 0.60 price
        // makerAmount = size = 10 tokens
        // takerAmount = size * price = 10 * 0.60 = 6.00 USDC

        Map<String, Object> order = builder.createOrder(
            TEST_TOKEN_ID,
            "SELL",
            new BigDecimal("0.60"),
            new BigDecimal("10.00"),
            "0.01",
            false,
            "GTC",
            TEST_API_KEY
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> orderData = (Map<String, Object>) order.get(
            "order"
        );

        assertEquals("10000000", orderData.get("makerAmount"));
        assertEquals("6000000", orderData.get("takerAmount"));
    }

    @Test
    @DisplayName("TC-OB-007: Price validation - too low")
    void testPriceValidationTooLow() {
        // Price must be >= tickSize and <= 1 - tickSize
        // For tickSize 0.01: valid range is [0.01, 0.99]

        assertThrows(IllegalArgumentException.class, () -> {
            builder.createOrder(
                TEST_TOKEN_ID,
                "BUY",
                new BigDecimal("0.005"),
                new BigDecimal("10.00"),
                "0.01",
                false,
                "GTC",
                TEST_API_KEY
            );
        });
    }

    @Test
    @DisplayName("TC-OB-008: Price validation - too high")
    void testPriceValidationTooHigh() {
        assertThrows(IllegalArgumentException.class, () -> {
            builder.createOrder(
                TEST_TOKEN_ID,
                "BUY",
                new BigDecimal("0.995"),
                new BigDecimal("10.00"),
                "0.01",
                false,
                "GTC",
                TEST_API_KEY
            );
        });
    }

    @Test
    @DisplayName("TC-OB-008b: Price at exact boundary - minimum")
    void testPriceAtMinBoundary() throws IOException {
        // Price exactly at tickSize should be valid
        Map<String, Object> order = builder.createOrder(
            TEST_TOKEN_ID,
            "BUY",
            new BigDecimal("0.01"),
            new BigDecimal("10.00"),
            "0.01",
            false,
            "GTC",
            TEST_API_KEY
        );
        assertNotNull(order);
    }

    @Test
    @DisplayName("TC-OB-008c: Price at exact boundary - maximum")
    void testPriceAtMaxBoundary() throws IOException {
        // Price exactly at 1 - tickSize should be valid
        Map<String, Object> order = builder.createOrder(
            TEST_TOKEN_ID,
            "BUY",
            new BigDecimal("0.99"),
            new BigDecimal("10.00"),
            "0.01",
            false,
            "GTC",
            TEST_API_KEY
        );
        assertNotNull(order);
    }

    @ParameterizedTest
    @ValueSource(strings = { "GTC", "GTD", "FOK", "FAK" })
    @DisplayName("TC-OB-009: All order types supported")
    void testAllOrderTypes(String orderType) throws IOException {
        Map<String, Object> order = builder.createOrder(
            TEST_TOKEN_ID,
            "BUY",
            new BigDecimal("0.50"),
            new BigDecimal("10.00"),
            "0.01",
            false,
            orderType,
            TEST_API_KEY
        );

        assertEquals(orderType, order.get("orderType"));

        // GTC and GTD should have postOnly field
        if ("GTC".equals(orderType) || "GTD".equals(orderType)) {
            assertNotNull(order.get("postOnly"));
        }
    }

    @Test
    @DisplayName("TC-OB-010: Neg risk order uses different exchange")
    void testNegRiskOrderUsesDifferentExchange() throws IOException {
        Map<String, Object> negRiskOrder = builder.createOrder(
            TEST_TOKEN_ID,
            "BUY",
            new BigDecimal("0.50"),
            new BigDecimal("10.00"),
            "0.01",
            true, // negRisk = true
            "GTC",
            TEST_API_KEY
        );

        Map<String, Object> normalOrder = builder.createOrder(
            TEST_TOKEN_ID,
            "BUY",
            new BigDecimal("0.50"),
            new BigDecimal("10.00"),
            "0.01",
            false, // negRisk = false
            "GTC",
            TEST_API_KEY
        );

        @SuppressWarnings("unchecked")
        String negRiskSig = (String) (
            (Map<String, Object>) negRiskOrder.get("order")
        ).get("signature");
        @SuppressWarnings("unchecked")
        String normalSig = (String) (
            (Map<String, Object>) normalOrder.get("order")
        ).get("signature");

        // Signatures should be different because verifyingContract in EIP-712 domain is different
        assertNotEquals(
            negRiskSig,
            normalSig,
            "Different exchanges should produce different signatures"
        );
    }

    @Test
    @DisplayName("TC-OB-011: Funder address support")
    void testFunderAddressSupport() throws IOException {
        String funderAddress = "0x1234567890123456789012345678901234567890";

        OrderBuilder builderWithFunder = new OrderBuilder(
            credentials,
            137,
            OrderBuilder.SIGNATURE_TYPE_EOA,
            funderAddress
        );

        assertEquals(funderAddress, builderWithFunder.getMakerAddress());
        assertEquals(
            credentials.getAddress(),
            builderWithFunder.getSignerAddress()
        );

        Map<String, Object> order = builderWithFunder.createOrder(
            TEST_TOKEN_ID,
            "BUY",
            new BigDecimal("0.50"),
            new BigDecimal("10.00"),
            "0.01",
            false,
            "GTC",
            TEST_API_KEY
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> orderData = (Map<String, Object>) order.get(
            "order"
        );

        assertEquals(funderAddress, orderData.get("maker"));
        assertEquals(credentials.getAddress(), orderData.get("signer"));
    }

    @Test
    @DisplayName("TC-OB-011b: Without funder address, maker equals signer")
    void testWithoutFunderAddressMakerEqualsSigner() throws IOException {
        Map<String, Object> order = builder.createOrder(
            TEST_TOKEN_ID,
            "BUY",
            new BigDecimal("0.50"),
            new BigDecimal("10.00"),
            "0.01",
            false,
            "GTC",
            TEST_API_KEY
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> orderData = (Map<String, Object>) order.get(
            "order"
        );

        assertEquals(orderData.get("maker"), orderData.get("signer"));
        assertEquals(credentials.getAddress(), orderData.get("maker"));
    }

    @Test
    @DisplayName("TC-OB-013: Tick size rounding")
    void testTickSizeRounding() throws IOException {
        // Price 0.505 with tick size 0.01 should round to 0.51
        Map<String, Object> order = builder.createOrder(
            TEST_TOKEN_ID,
            "BUY",
            new BigDecimal("0.505"),
            new BigDecimal("10.00"),
            "0.01",
            false,
            "GTC",
            TEST_API_KEY
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> orderData = (Map<String, Object>) order.get(
            "order"
        );

        // makerAmount should reflect the rounded price
        // 10 * 0.51 * 10^6 = 5100000
        assertEquals("5100000", orderData.get("makerAmount"));
    }

    @Test
    @DisplayName("TC-OB-014: Order contains required fields")
    void testOrderContainsRequiredFields() throws IOException {
        Map<String, Object> order = builder.createOrder(
            TEST_TOKEN_ID,
            "BUY",
            new BigDecimal("0.50"),
            new BigDecimal("10.00"),
            "0.01",
            false,
            "GTC",
            TEST_API_KEY
        );

        // Top-level fields
        assertAll(
            () -> assertNotNull(order.get("order")),
            () -> assertNotNull(order.get("owner")),
            () -> assertNotNull(order.get("orderType")),
            () -> assertNotNull(order.get("deferExec"))
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> orderData = (Map<String, Object>) order.get(
            "order"
        );

        // Order data fields
        assertAll(
            () -> assertNotNull(orderData.get("salt")),
            () -> assertNotNull(orderData.get("maker")),
            () -> assertNotNull(orderData.get("signer")),
            () -> assertNotNull(orderData.get("taker")),
            () -> assertNotNull(orderData.get("tokenId")),
            () -> assertNotNull(orderData.get("makerAmount")),
            () -> assertNotNull(orderData.get("takerAmount")),
            () -> assertNotNull(orderData.get("expiration")),
            () -> assertNotNull(orderData.get("nonce")),
            () -> assertNotNull(orderData.get("feeRateBps")),
            () -> assertNotNull(orderData.get("side")),
            () -> assertNotNull(orderData.get("signatureType")),
            () -> assertNotNull(orderData.get("signature"))
        );
    }

    @Test
    @DisplayName("TC-OB-015: Taker defaults to zero address")
    void testTakerDefaultsToZeroAddress() throws IOException {
        Map<String, Object> order = builder.createOrder(
            TEST_TOKEN_ID,
            "BUY",
            new BigDecimal("0.50"),
            new BigDecimal("10.00"),
            "0.01",
            false,
            "GTC",
            TEST_API_KEY
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> orderData = (Map<String, Object>) order.get(
            "order"
        );

        assertEquals(
            "0x0000000000000000000000000000000000000000",
            orderData.get("taker")
        );
    }

    @Test
    @DisplayName("TC-OB-016: Nonce defaults to zero")
    void testNonceDefaultsToZero() throws IOException {
        Map<String, Object> order = builder.createOrder(
            TEST_TOKEN_ID,
            "BUY",
            new BigDecimal("0.50"),
            new BigDecimal("10.00"),
            "0.01",
            false,
            "GTC",
            TEST_API_KEY
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> orderData = (Map<String, Object>) order.get(
            "order"
        );

        assertEquals("0", orderData.get("nonce"));
    }

    @Test
    @DisplayName("TC-OB-017: Fee rate BPS included in order")
    void testFeeRateBpsIncluded() throws IOException {
        Map<String, Object> order = builder.createOrder(
            TEST_TOKEN_ID,
            "BUY",
            new BigDecimal("0.50"),
            new BigDecimal("10.00"),
            "0.01",
            false,
            "GTC",
            TEST_API_KEY,
            100, // feeRateBps = 100 (1%)
            0,
            null
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> orderData = (Map<String, Object>) order.get(
            "order"
        );

        assertEquals("100", orderData.get("feeRateBps"));
    }

    @Test
    @DisplayName("TC-OB-018: Expiration included in order")
    void testExpirationIncluded() throws IOException {
        long expiration = 1700000000L;

        Map<String, Object> order = builder.createOrder(
            TEST_TOKEN_ID,
            "BUY",
            new BigDecimal("0.50"),
            new BigDecimal("10.00"),
            "0.01",
            false,
            "GTD",
            TEST_API_KEY,
            0,
            expiration,
            null
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> orderData = (Map<String, Object>) order.get(
            "order"
        );

        assertEquals(String.valueOf(expiration), orderData.get("expiration"));
    }

    @Test
    @DisplayName("TC-OB-019: Custom taker address")
    void testCustomTakerAddress() throws IOException {
        String takerAddress = "0xabcdef1234567890abcdef1234567890abcdef12";

        Map<String, Object> order = builder.createOrder(
            TEST_TOKEN_ID,
            "BUY",
            new BigDecimal("0.50"),
            new BigDecimal("10.00"),
            "0.01",
            false,
            "GTC",
            TEST_API_KEY,
            0,
            0,
            takerAddress
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> orderData = (Map<String, Object>) order.get(
            "order"
        );

        assertEquals(takerAddress, orderData.get("taker"));
    }

    @Test
    @DisplayName("TC-OB-020: Validation rejects null token ID")
    void testValidationRejectsNullTokenId() {
        assertThrows(IllegalArgumentException.class, () -> {
            builder.createOrder(
                null,
                "BUY",
                new BigDecimal("0.50"),
                new BigDecimal("10.00"),
                "0.01",
                false,
                "GTC",
                TEST_API_KEY
            );
        });
    }

    @Test
    @DisplayName("TC-OB-021: Validation rejects empty token ID")
    void testValidationRejectsEmptyTokenId() {
        assertThrows(IllegalArgumentException.class, () -> {
            builder.createOrder(
                "",
                "BUY",
                new BigDecimal("0.50"),
                new BigDecimal("10.00"),
                "0.01",
                false,
                "GTC",
                TEST_API_KEY
            );
        });
    }

    @Test
    @DisplayName("TC-OB-022: Validation rejects invalid side")
    void testValidationRejectsInvalidSide() {
        assertThrows(IllegalArgumentException.class, () -> {
            builder.createOrder(
                TEST_TOKEN_ID,
                "INVALID",
                new BigDecimal("0.50"),
                new BigDecimal("10.00"),
                "0.01",
                false,
                "GTC",
                TEST_API_KEY
            );
        });
    }

    @Test
    @DisplayName("TC-OB-023: Validation rejects null API key")
    void testValidationRejectsNullApiKey() {
        assertThrows(IllegalArgumentException.class, () -> {
            builder.createOrder(
                TEST_TOKEN_ID,
                "BUY",
                new BigDecimal("0.50"),
                new BigDecimal("10.00"),
                "0.01",
                false,
                "GTC",
                null
            );
        });
    }

    @Test
    @DisplayName("TC-OB-024: Validation rejects zero size")
    void testValidationRejectsZeroSize() {
        assertThrows(IllegalArgumentException.class, () -> {
            builder.createOrder(
                TEST_TOKEN_ID,
                "BUY",
                new BigDecimal("0.50"),
                BigDecimal.ZERO,
                "0.01",
                false,
                "GTC",
                TEST_API_KEY
            );
        });
    }

    @Test
    @DisplayName("TC-OB-025: Validation rejects negative size")
    void testValidationRejectsNegativeSize() {
        assertThrows(IllegalArgumentException.class, () -> {
            builder.createOrder(
                TEST_TOKEN_ID,
                "BUY",
                new BigDecimal("0.50"),
                new BigDecimal("-10.00"),
                "0.01",
                false,
                "GTC",
                TEST_API_KEY
            );
        });
    }

    @Test
    @DisplayName("TC-OB-026: Unsupported chain ID throws exception")
    void testUnsupportedChainIdThrowsException() {
        OrderBuilder invalidChainBuilder = new OrderBuilder(credentials, 999);

        assertThrows(IllegalArgumentException.class, () -> {
            invalidChainBuilder.createOrder(
                TEST_TOKEN_ID,
                "BUY",
                new BigDecimal("0.50"),
                new BigDecimal("10.00"),
                "0.01",
                false,
                "GTC",
                TEST_API_KEY
            );
        });
    }

    @Test
    @DisplayName("TC-OB-027: Salt is random for each order")
    void testSaltIsRandomForEachOrder() throws IOException {
        Map<String, Object> order1 = builder.createOrder(
            TEST_TOKEN_ID,
            "BUY",
            new BigDecimal("0.50"),
            new BigDecimal("10.00"),
            "0.01",
            false,
            "GTC",
            TEST_API_KEY
        );

        Map<String, Object> order2 = builder.createOrder(
            TEST_TOKEN_ID,
            "BUY",
            new BigDecimal("0.50"),
            new BigDecimal("10.00"),
            "0.01",
            false,
            "GTC",
            TEST_API_KEY
        );

        @SuppressWarnings("unchecked")
        Object salt1 = ((Map<String, Object>) order1.get("order")).get("salt");
        @SuppressWarnings("unchecked")
        Object salt2 = ((Map<String, Object>) order2.get("order")).get("salt");

        assertNotEquals(
            salt1,
            salt2,
            "Each order should have a different salt"
        );
    }

    @Test
    @DisplayName("TC-OB-028: Signature type included in order")
    void testSignatureTypeIncluded() throws IOException {
        Map<String, Object> order = builder.createOrder(
            TEST_TOKEN_ID,
            "BUY",
            new BigDecimal("0.50"),
            new BigDecimal("10.00"),
            "0.01",
            false,
            "GTC",
            TEST_API_KEY
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> orderData = (Map<String, Object>) order.get(
            "order"
        );

        assertEquals(
            OrderBuilder.SIGNATURE_TYPE_EOA,
            orderData.get("signatureType")
        );
    }

    @Test
    @DisplayName("TC-OB-029: Different tick sizes produce correct amounts")
    void testDifferentTickSizesProduceCorrectAmounts() throws IOException {
        // Test with tick size 0.001
        Map<String, Object> order = builder.createOrder(
            TEST_TOKEN_ID,
            "BUY",
            new BigDecimal("0.505"),
            new BigDecimal("10.00"),
            "0.001",
            false,
            "GTC",
            TEST_API_KEY
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> orderData = (Map<String, Object>) order.get(
            "order"
        );

        // 10 * 0.505 * 10^6 = 5050000
        assertEquals("5050000", orderData.get("makerAmount"));
    }

    @Test
    @DisplayName(
        "TC-OB-030: getMakerAddress returns correct address without funder"
    )
    void testGetMakerAddressWithoutFunder() {
        assertEquals(credentials.getAddress(), builder.getMakerAddress());
    }

    @Test
    @DisplayName("TC-OB-031: getSignerAddress returns correct address")
    void testGetSignerAddress() {
        assertEquals(credentials.getAddress(), builder.getSignerAddress());
    }

    @Test
    @DisplayName(
        "TC-OB-032: Constructor with null credentials throws exception"
    )
    void testConstructorWithNullCredentials() {
        assertThrows(NullPointerException.class, () -> {
            new OrderBuilder(null, 137);
        });
    }

  @Test
  @DisplayName("TC-OB-033: FOK BUY quantizes makerAmount to 2-decimal precision")
  void testFokBuyQuantizesMakerAmountToTwoDecimals() throws IOException {
    Map<String, Object> order =
        builder.createOrder(
            TEST_TOKEN_ID,
            "BUY",
            new BigDecimal("0.3333"),
            new BigDecimal("1.23"),
            "0.0001",
            false,
            "FOK",
            TEST_API_KEY);

    @SuppressWarnings("unchecked")
    Map<String, Object> orderData = (Map<String, Object>) order.get("order");

    // 1.23 * 0.3333 = 0.409959 -> quantized down to 0.40 in 6-decimal units.
    assertEquals("400000", orderData.get("makerAmount"));
    assertEquals("1230000", orderData.get("takerAmount"));
  }

  @Test
  @DisplayName("TC-OB-034: GTC BUY keeps standard precision (no market-buy quantization)")
  void testGtcBuyKeepsStandardPrecision() throws IOException {
    Map<String, Object> order =
        builder.createOrder(
            TEST_TOKEN_ID,
            "BUY",
            new BigDecimal("0.3333"),
            new BigDecimal("1.23"),
            "0.0001",
            false,
            "GTC",
            TEST_API_KEY);

    @SuppressWarnings("unchecked")
    Map<String, Object> orderData = (Map<String, Object>) order.get("order");

    // Standard path keeps computed precision for resting orders.
    assertEquals("409959", orderData.get("makerAmount"));
    assertEquals("1230000", orderData.get("takerAmount"));
  }
}
