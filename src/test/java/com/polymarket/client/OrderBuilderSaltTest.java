package com.polymarket.client;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;

/**
 * Tests for IEEE 754 salt masking in OrderBuilder.
 *
 * <p>Reference: rs-clob-client/src/clob/order_builder.rs — {@code fn to_ieee_754_int(salt: u64)}
 */
@DisplayName("OrderBuilder Salt Tests")
class OrderBuilderSaltTest {

    private static final String TEST_PRIVATE_KEY =
        "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final long IEEE_754_MAX = (1L << 53) - 1L; // 9_007_199_254_740_991
    private static final String TOKEN_ID = "12345";
    private static final String API_KEY = "test-key";

    private OrderBuilder builder;

    @BeforeEach
    void setUp() {
        Credentials credentials = Credentials.create(TEST_PRIVATE_KEY);
        builder = new OrderBuilder(credentials, 137);
    }

    @SuppressWarnings("unchecked")
    private long buildAndGetSalt() {
        Map<String, Object> result = builder.createOrder(
            TOKEN_ID, "BUY", new BigDecimal("0.50"), new BigDecimal("10.00"),
            "0.01", false, "GTC", API_KEY
        );
        Map<String, Object> order = (Map<String, Object>) result.get("order");
        return ((Number) order.get("salt")).longValue();
    }

    @Test
    @DisplayName("TC-OB-SALT-001: Salt is always <= 2^53 - 1 (IEEE 754 safe integer range)")
    void saltIsAlwaysWithinIeee754Range() {
        for (int i = 0; i < 1000; i++) {
            long salt = buildAndGetSalt();
            assertTrue(
                salt <= IEEE_754_MAX,
                "Salt " + salt + " exceeds 2^53-1 on iteration " + i
            );
        }
    }

    @Test
    @DisplayName("TC-OB-SALT-002: Salt is always >= 0 (no negative values)")
    void saltIsAlwaysNonNegative() {
        for (int i = 0; i < 1000; i++) {
            long salt = buildAndGetSalt();
            assertTrue(salt >= 0, "Salt was negative on iteration " + i);
        }
    }

    @Test
    @DisplayName("TC-OB-SALT-003: Successive calls produce different salts (entropy check)")
    void successiveCallsProduceDifferentSalts() {
        Set<Long> salts = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            salts.add(buildAndGetSalt());
        }
        // With 2^53 possible values it's essentially impossible to get 1 unique value in 20 tries
        assertTrue(salts.size() > 1, "Expected diverse salts but got: " + salts);
    }
}
