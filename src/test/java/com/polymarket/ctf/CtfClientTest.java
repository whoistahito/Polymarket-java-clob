package com.polymarket.ctf;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.web3j.utils.Numeric;

/**
 * Unit tests for the Conditional Token Framework client.
 *
 * <p>Test coverage mirrors the Rust SDK test suite in {@code rs-clob-client/tests/ctf.rs}.
 */
@DisplayName("CtfClient")
class CtfClientTest {

    // -------------------------------------------------------------------------
    // Well-known test constants (same as Rust tests)
    // -------------------------------------------------------------------------
    private static final String USDC_POLYGON =
        "0x2791Bca1f2de4661ED88A30C99A7a9449Aa84174";
    private static final String ZERO_BYTES32 =
        "0x" + "0".repeat(64);
    private static final String QUESTION_ID =
        "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";
    private static final String ORACLE =
        "0x0000000000000000000000000000000000000001";

    // -------------------------------------------------------------------------
    // TC-CTF-003, TC-CTF-006 — parentCollectionId defaults
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("Request builders")
    class RequestBuilders {

        @Test
        @DisplayName("TC-CTF-003 — CollectionIdRequest parentCollectionId defaults to zero")
        void collectionIdRequestDefaultParent() {
            CollectionIdRequest req = CollectionIdRequest.builder()
                .conditionId(ZERO_BYTES32)
                .indexSet(BigInteger.ONE)
                .build();

            assertEquals(ZERO_BYTES32, req.getParentCollectionId());
        }

        @Test
        @DisplayName("TC-CTF-006 — SplitPositionRequest parentCollectionId defaults to zero")
        void splitPositionRequestDefaultParent() {
            SplitPositionRequest req = SplitPositionRequest.builder()
                .collateralToken(USDC_POLYGON)
                .conditionId(ZERO_BYTES32)
                .partition(SplitPositionRequest.BINARY_PARTITION)
                .amount(BigInteger.ONE)
                .build();

            assertEquals(ZERO_BYTES32, req.getParentCollectionId());
        }
    }

    // -------------------------------------------------------------------------
    // TC-CTF-010 to TC-CTF-014 — Binary market convenience methods
    // (mirrors Rust binary_market_convenience_methods module)
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("Binary market convenience methods")
    class BinaryMarketConvenienceMethods {

        @Test
        @DisplayName("TC-CTF-010 — BINARY_PARTITION constant equals [1, 2]")
        void binaryPartitionConstant() {
            assertEquals(
                List.of(BigInteger.ONE, BigInteger.TWO),
                SplitPositionRequest.BINARY_PARTITION
            );
        }

        @Test
        @DisplayName("TC-CTF-011 — SplitPositionRequest.forBinaryMarket sets correct fields")
        void splitPositionForBinaryMarket() {
            SplitPositionRequest req =
                SplitPositionRequest.forBinaryMarket(USDC_POLYGON, ZERO_BYTES32,
                    BigInteger.valueOf(1_000_000L));

            assertEquals(USDC_POLYGON, req.getCollateralToken());
            assertEquals(ZERO_BYTES32, req.getConditionId());
            assertEquals(List.of(BigInteger.ONE, BigInteger.TWO), req.getPartition());
            assertEquals(BigInteger.valueOf(1_000_000L), req.getAmount());
            assertEquals(ZERO_BYTES32, req.getParentCollectionId());
        }

        @Test
        @DisplayName("TC-CTF-012 — MergePositionsRequest.forBinaryMarket sets correct fields")
        void mergePositionsForBinaryMarket() {
            MergePositionsRequest req =
                MergePositionsRequest.forBinaryMarket(USDC_POLYGON, ZERO_BYTES32,
                    BigInteger.valueOf(1_000_000L));

            assertEquals(USDC_POLYGON, req.getCollateralToken());
            assertEquals(ZERO_BYTES32, req.getConditionId());
            assertEquals(List.of(BigInteger.ONE, BigInteger.TWO), req.getPartition());
            assertEquals(BigInteger.valueOf(1_000_000L), req.getAmount());
            assertEquals(ZERO_BYTES32, req.getParentCollectionId());
        }

        @Test
        @DisplayName("TC-CTF-013 — RedeemPositionsRequest.forBinaryMarket sets correct fields")
        void redeemPositionsForBinaryMarket() {
            RedeemPositionsRequest req =
                RedeemPositionsRequest.forBinaryMarket(USDC_POLYGON, ZERO_BYTES32);

            assertEquals(USDC_POLYGON, req.getCollateralToken());
            assertEquals(ZERO_BYTES32, req.getConditionId());
            assertEquals(List.of(BigInteger.ONE, BigInteger.TWO), req.getIndexSets());
            assertEquals(ZERO_BYTES32, req.getParentCollectionId());
        }
    }

    // -------------------------------------------------------------------------
    // TC-CTF-015 to TC-CTF-019 — Client creation
    // (mirrors Rust client_creation module)
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("Client creation")
    class ClientCreation {

        @Test
        @DisplayName("TC-CTF-015 — Polygon mainnet (137) should succeed")
        void polygonMainnetShouldSucceed() {
            assertDoesNotThrow(() -> CtfClient.forChain(137));
        }

        @Test
        @DisplayName("TC-CTF-016 — Amoy testnet (80002) should succeed")
        void amoyTestnetShouldSucceed() {
            assertDoesNotThrow(() -> CtfClient.forChain(80002));
        }

        @Test
        @DisplayName("TC-CTF-017 — Unsupported chain ID should throw CtfException")
        void invalidChainShouldFail() {
            CtfException ex = assertThrows(CtfException.class, () -> CtfClient.forChain(999));
            assertTrue(ex.getMessage().contains("999"));
        }

        @Test
        @DisplayName("TC-CTF-018 — forChainWithNegRisk on Polygon should succeed")
        void withNegRiskPolygonShouldSucceed() {
            CtfClient client = assertDoesNotThrow(() -> CtfClient.forChainWithNegRisk(137));
            assertTrue(client.hasNegRisk());
        }

        @Test
        @DisplayName("TC-CTF-019 — Standard client has no NegRisk adapter")
        void standardClientHasNoNegRisk() {
            CtfClient client = CtfClient.forChain(137);
            assertFalse(client.hasNegRisk());
        }

        @Test
        @DisplayName("TC-CTF-020 — Standard client has no provider")
        void standardClientHasNoProvider() {
            CtfClient client = CtfClient.forChain(137);
            assertFalse(client.hasProvider());
        }
    }

    // -------------------------------------------------------------------------
    // TC-CTF-021 to TC-CTF-029 — ID computation (pure local math)
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("ID computation (pure local math)")
    class IdComputation {

        private final CtfClient ctf = CtfClient.forChain(137);

        @Test
        @DisplayName("TC-CTF-021 — conditionId returns 32-byte hex result")
        void conditionIdReturns32ByteHex() {
            ConditionIdResponse resp = ctf.conditionId(ConditionIdRequest.builder()
                .oracle(ORACLE)
                .questionId(ZERO_BYTES32)
                .outcomeSlotCount(BigInteger.TWO)
                .build());

            assertNotNull(resp.conditionId());
            // "0x" + 64 hex chars = 66 chars
            assertEquals(66, resp.conditionId().length());
            assertTrue(resp.conditionId().startsWith("0x"));
        }

        @Test
        @DisplayName("TC-CTF-022 — conditionId is deterministic")
        void conditionIdIsDeterministic() {
            ConditionIdRequest req = ConditionIdRequest.builder()
                .oracle(ORACLE)
                .questionId(QUESTION_ID)
                .outcomeSlotCount(BigInteger.TWO)
                .build();

            assertEquals(ctf.conditionId(req).conditionId(), ctf.conditionId(req).conditionId());
        }

        @Test
        @DisplayName("TC-CTF-023 — conditionId differs for different inputs")
        void conditionIdDiffersForDifferentInputs() {
            ConditionIdResponse r1 = ctf.conditionId(ConditionIdRequest.builder()
                .oracle(ORACLE)
                .questionId(ZERO_BYTES32)
                .outcomeSlotCount(BigInteger.TWO)
                .build());

            ConditionIdResponse r2 = ctf.conditionId(ConditionIdRequest.builder()
                .oracle(ORACLE)
                .questionId(QUESTION_ID)
                .outcomeSlotCount(BigInteger.TWO)
                .build());

            assertNotEquals(r1.conditionId(), r2.conditionId());
        }

        @Test
        @DisplayName("TC-CTF-024 — conditionId matches known keccak256 value")
        void conditionIdMatchesKnownValue() {
            // Manually compute: keccak256(encodePacked(oracle(20b), questionId(32b), slotCount(32b)))
            // oracle  = 0x0000000000000000000000000000000000000001 (20 bytes)
            // questionId = 0x0000...0000 (32 bytes)
            // slotCount  = 2 (32 bytes big-endian = 0x0000...0002)
            byte[] oracle20   = CtfClient.addressToBytes20(ORACLE);
            byte[] qId32      = CtfClient.hexToBytes32(ZERO_BYTES32);
            byte[] slotCount  = CtfClient.toBigEndian32(BigInteger.TWO);
            byte[] packed     = CtfClient.concat(oracle20, qId32, slotCount);
            byte[] expected   = org.web3j.crypto.Hash.sha3(packed);
            String expectedHex = Numeric.toHexString(expected);

            ConditionIdResponse resp = ctf.conditionId(ConditionIdRequest.builder()
                .oracle(ORACLE)
                .questionId(ZERO_BYTES32)
                .outcomeSlotCount(BigInteger.TWO)
                .build());

            assertEquals(expectedHex, resp.conditionId());
        }

        @Test
        @DisplayName("TC-CTF-025 — collectionId returns 32-byte hex result")
        void collectionIdReturns32ByteHex() {
            CollectionIdResponse resp = ctf.collectionId(CollectionIdRequest.builder()
                .conditionId(ZERO_BYTES32)
                .indexSet(BigInteger.ONE)
                .build());

            assertNotNull(resp.collectionId());
            assertEquals(66, resp.collectionId().length());
            assertTrue(resp.collectionId().startsWith("0x"));
        }

        @Test
        @DisplayName("TC-CTF-026 — collectionId with zero parent and zero conditionId equals inner hash")
        void collectionIdWithZeroParentEqualsInnerHash() {
            // For zero parentCollectionId: result = 0x00..00 XOR keccak256(condId||indexSet)
            //   which is just keccak256(condId||indexSet)
            byte[] condId   = CtfClient.hexToBytes32(ZERO_BYTES32);
            byte[] idxSet   = CtfClient.toBigEndian32(BigInteger.ONE);
            byte[] expected = org.web3j.crypto.Hash.sha3(CtfClient.concat(condId, idxSet));
            String expectedHex = Numeric.toHexString(expected);

            CollectionIdResponse resp = ctf.collectionId(CollectionIdRequest.builder()
                .conditionId(ZERO_BYTES32)
                .indexSet(BigInteger.ONE)
                .build());

            assertEquals(expectedHex, resp.collectionId());
        }

        @Test
        @DisplayName("TC-CTF-027 — collectionId differs for YES vs NO index set")
        void collectionIdDiffersForYesVsNo() {
            CollectionIdResponse yes = ctf.collectionId(CollectionIdRequest.builder()
                .conditionId(ZERO_BYTES32)
                .indexSet(BigInteger.ONE)    // YES
                .build());
            CollectionIdResponse no = ctf.collectionId(CollectionIdRequest.builder()
                .conditionId(ZERO_BYTES32)
                .indexSet(BigInteger.TWO)    // NO
                .build());

            assertNotEquals(yes.collectionId(), no.collectionId());
        }

        @Test
        @DisplayName("TC-CTF-028 — positionId returns positive uint256")
        void positionIdReturnsPositiveBigInteger() {
            PositionIdResponse resp = ctf.positionId(PositionIdRequest.builder()
                .collateralToken(USDC_POLYGON)
                .collectionId(ZERO_BYTES32)
                .build());

            assertNotNull(resp.positionId());
            assertTrue(resp.positionId().compareTo(BigInteger.ZERO) > 0);
        }

        @Test
        @DisplayName("TC-CTF-029 — positionId matches known keccak256 value")
        void positionIdMatchesKnownValue() {
            // positionId = uint256(keccak256(encodePacked(collateralToken(20b), collectionId(32b))))
            byte[] token20   = CtfClient.addressToBytes20(USDC_POLYGON);
            byte[] collId32  = CtfClient.hexToBytes32(ZERO_BYTES32);
            byte[] packed    = CtfClient.concat(token20, collId32);
            byte[] hashBytes = org.web3j.crypto.Hash.sha3(packed);
            BigInteger expected = new BigInteger(1, hashBytes);

            PositionIdResponse resp = ctf.positionId(PositionIdRequest.builder()
                .collateralToken(USDC_POLYGON)
                .collectionId(ZERO_BYTES32)
                .build());

            assertEquals(expected, resp.positionId());
        }
    }

    // -------------------------------------------------------------------------
    // TC-CTF-030 — NegRisk (no on-chain operations; structural tests)
    // (mirrors Rust neg_risk module)
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("NegRisk")
    class NegRisk {

        @Test
        @DisplayName("TC-CTF-030 — redeemNegRisk without NegRisk adapter throws CtfException")
        void redeemNegRiskWithoutAdapterThrows() {
            CtfClient client = CtfClient.forChain(137);   // no neg-risk, no provider
            RedeemNegRiskRequest req = RedeemNegRiskRequest.builder()
                .conditionId(ZERO_BYTES32)
                .amounts(List.of(BigInteger.valueOf(500_000L), BigInteger.valueOf(500_000L)))
                .build();

            CtfException ex = assertThrows(CtfException.class, () -> client.redeemNegRisk(req));
            assertTrue(ex.getMessage().toLowerCase().contains("provider")
                || ex.getMessage().toLowerCase().contains("negrisk"));
        }

        @Test
        @DisplayName("TC-CTF-031 — redeemNegRisk with adapter but no provider throws CtfException")
        void redeemNegRiskWithAdapterButNoProviderThrows() {
            CtfClient client = CtfClient.forChainWithNegRisk(137);  // neg-risk, but no provider
            RedeemNegRiskRequest req = RedeemNegRiskRequest.builder()
                .conditionId(ZERO_BYTES32)
                .amounts(List.of(BigInteger.valueOf(500_000L)))
                .build();

            // Should fail on requireProvider() before even checking neg-risk adapter
            assertThrows(CtfException.class, () -> client.redeemNegRisk(req));
        }
    }

    // -------------------------------------------------------------------------
    // TC-CTF-032 — Internal helper utilities
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("Helper utilities")
    class HelperUtilities {

        @Test
        @DisplayName("TC-CTF-032 — addressToBytes20 returns exactly 20 bytes")
        void addressToBytes20Returns20Bytes() {
            byte[] result = CtfClient.addressToBytes20(USDC_POLYGON);
            assertEquals(20, result.length);
        }

        @Test
        @DisplayName("TC-CTF-033 — hexToBytes32 returns exactly 32 bytes")
        void hexToBytes32Returns32Bytes() {
            byte[] result = CtfClient.hexToBytes32(ZERO_BYTES32);
            assertEquals(32, result.length);
            // All zeros
            for (byte b : result) assertEquals(0, b);
        }

        @Test
        @DisplayName("TC-CTF-034 — toBigEndian32 encodes BigInteger.TWO correctly")
        void toBigEndian32EncodesTwoCorrectly() {
            byte[] result = CtfClient.toBigEndian32(BigInteger.TWO);
            assertEquals(32, result.length);
            assertEquals(2, result[31]);
            for (int i = 0; i < 31; i++) assertEquals(0, result[i]);
        }

        @Test
        @DisplayName("TC-CTF-035 — xor32 with same arrays returns zero array")
        void xor32WithSameArrayReturnsZero() {
            byte[] a = new byte[32];
            a[0] = 0x42;
            a[15] = (byte) 0xff;
            byte[] result = CtfClient.xor32(a, a);
            for (byte b : result) assertEquals(0, b);
        }

        @Test
        @DisplayName("TC-CTF-036 — concat produces correct total length")
        void concatProducesCorrectLength() {
            byte[] a = new byte[20];
            byte[] b = new byte[32];
            byte[] result = CtfClient.concat(a, b);
            assertEquals(52, result.length);
        }
    }
}
