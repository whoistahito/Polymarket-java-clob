package com.polymarket.ctf;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.RawTransactionManager;
import org.web3j.utils.Numeric;

/**
 * Client for interacting with the Gnosis Conditional Token Framework (CTF) contract.
 *
 * <h2>ID Calculations (no provider required)</h2>
 * <ul>
 *   <li>{@link #conditionId(ConditionIdRequest)} — pure keccak256 computation, no RPC needed</li>
 *   <li>{@link #collectionId(CollectionIdRequest)} — pure XOR computation, no RPC needed</li>
 *   <li>{@link #positionId(PositionIdRequest)} — pure keccak256 computation, no RPC needed</li>
 * </ul>
 *
 * <h2>On-chain Operations (provider + credentials required)</h2>
 * <ul>
 *   <li>{@link #splitPosition(SplitPositionRequest)}</li>
 *   <li>{@link #mergePositions(MergePositionsRequest)}</li>
 *   <li>{@link #redeemPositions(RedeemPositionsRequest)}</li>
 *   <li>{@link #redeemNegRisk(RedeemNegRiskRequest)}</li>
 * </ul>
 *
 * <h2>Usage — ID-only (no blockchain)</h2>
 * <pre>{@code
 * CtfClient ctf = CtfClient.forChain(137);
 * ConditionIdResponse r = ctf.conditionId(
 *     ConditionIdRequest.builder()
 *         .oracle("0x...")
 *         .questionId("0x...")
 *         .outcomeSlotCount(BigInteger.TWO)
 *         .build());
 * }</pre>
 *
 * <h2>Usage — full (with blockchain)</h2>
 * <pre>{@code
 * Web3j web3j = Web3j.build(new HttpService("https://polygon-rpc.com"));
 * Credentials credentials = Credentials.create("0x<private-key>");
 * CtfClient ctf = CtfClient.builder()
 *     .chainId(137)
 *     .web3j(web3j)
 *     .credentials(credentials)
 *     .build();
 * SplitPositionResponse resp = ctf.splitPosition(...);
 * }</pre>
 */
public final class CtfClient {

    // Contract addresses by chain ID
    private static final Map<Integer, String> CTF_CONTRACT_ADDRESS = Map.of(
        137,   "0x4D97DCd97eC945f40cF65F87097ACe5EA0476045",
        80002, "0x69308FB512518e39F9b16112fA8d994F4e2Bf8bB"
    );

    // NegRisk adapter addresses by chain ID
    private static final Map<Integer, String> NEG_RISK_ADAPTER_ADDRESS = Map.of(
        137,   "0xd91E80cF2E7be2e162c6513ceD06f1dD0dA35296",
        80002, "0xd91E80cF2E7be2e162c6513ceD06f1dD0dA35296"
    );

    private static final BigInteger DEFAULT_GAS_PRICE = BigInteger.valueOf(50_000_000_000L);
    private static final BigInteger DEFAULT_GAS_LIMIT = BigInteger.valueOf(300_000L);

    /** Poll interval (ms) and max attempts when waiting for a transaction receipt. */
    private static final long RECEIPT_POLL_INTERVAL_MS = 1_000L;
    private static final int  RECEIPT_MAX_ATTEMPTS = 60;

    private final int chainId;
    private final String contractAddress;
    private final String negRiskAdapterAddress; // null when not in neg-risk mode
    private final Web3j web3j;                  // null when ID-only mode
    private final Credentials credentials;       // null when ID-only mode
    private final BigInteger gasPrice;
    private final BigInteger gasLimit;

    private CtfClient(Builder builder) {
        this.chainId              = builder.chainId;
        this.contractAddress      = builder.contractAddress;
        this.negRiskAdapterAddress = builder.negRiskAdapterAddress;
        this.web3j                = builder.web3j;
        this.credentials          = builder.credentials;
        this.gasPrice             = builder.gasPrice != null ? builder.gasPrice : DEFAULT_GAS_PRICE;
        this.gasLimit             = builder.gasLimit != null ? builder.gasLimit : DEFAULT_GAS_LIMIT;
    }

    // -------------------------------------------------------------------------
    // Static factories
    // -------------------------------------------------------------------------

    /**
     * Creates an ID-only client (no Web3j provider needed) for the given chain.
     *
     * @param chainId 137 (Polygon) or 80002 (Amoy)
     * @return configured {@link CtfClient}
     * @throws CtfException if the chain ID is not supported
     */
    public static CtfClient forChain(int chainId) {
        return new Builder().chainId(chainId).build();
    }

    /**
     * Creates a full client with NegRisk adapter support for the given chain.
     *
     * @param chainId 137 (Polygon) or 80002 (Amoy)
     * @return configured {@link CtfClient} with NegRisk support
     * @throws CtfException if the chain ID is not supported
     */
    public static CtfClient forChainWithNegRisk(int chainId) {
        return new Builder().chainId(chainId).negRisk(true).build();
    }

    // -------------------------------------------------------------------------
    // ID calculations (pure local computation — no blockchain required)
    // -------------------------------------------------------------------------

    /**
     * Computes the condition ID for a given oracle, question ID, and outcome slot count.
     *
     * <p>Formula: {@code keccak256(encodePacked(oracle, questionId, outcomeSlotCount))}
     *
     * @param request the condition ID parameters
     * @return response containing the bytes32 hex condition ID
     */
    public ConditionIdResponse conditionId(ConditionIdRequest request) {
        byte[] oracle   = addressToBytes20(request.getOracle());
        byte[] qId      = hexToBytes32(request.getQuestionId());
        byte[] slotCount = toBigEndian32(request.getOutcomeSlotCount());

        byte[] packed = concat(oracle, qId, slotCount);
        byte[] hash   = Hash.sha3(packed);
        return new ConditionIdResponse(Numeric.toHexString(hash));
    }

    /**
     * Computes the collection ID for a given parent collection, condition ID, and index set.
     *
     * <p>Formula:
     * {@code parentCollectionId XOR keccak256(encodePacked(conditionId, indexSet))}
     *
     * @param request the collection ID parameters
     * @return response containing the bytes32 hex collection ID
     */
    public CollectionIdResponse collectionId(CollectionIdRequest request) {
        byte[] parent    = hexToBytes32(request.getParentCollectionId());
        byte[] condId    = hexToBytes32(request.getConditionId());
        byte[] indexSet  = toBigEndian32(request.getIndexSet());

        byte[] innerHash = Hash.sha3(concat(condId, indexSet));
        byte[] xored     = xor32(parent, innerHash);
        return new CollectionIdResponse(Numeric.toHexString(xored));
    }

    /**
     * Computes the ERC1155 position ID (token ID) from a collateral token and collection ID.
     *
     * <p>Formula:
     * {@code uint256(keccak256(encodePacked(collateralToken, collectionId)))}
     *
     * @param request the position ID parameters
     * @return response containing the uint256 position ID
     */
    public PositionIdResponse positionId(PositionIdRequest request) {
        byte[] token     = addressToBytes20(request.getCollateralToken());
        byte[] collId    = hexToBytes32(request.getCollectionId());

        byte[] packed = concat(token, collId);
        byte[] hash   = Hash.sha3(packed);
        return new PositionIdResponse(new BigInteger(1, hash));
    }

    // -------------------------------------------------------------------------
    // On-chain operations (require web3j + credentials)
    // -------------------------------------------------------------------------

    /**
     * Splits collateral into outcome token pairs on-chain.
     *
     * @param request split parameters
     * @return transaction hash and block number
     * @throws IOException    if a network error occurs
     * @throws CtfException   if the client has no provider configured
     */
    public SplitPositionResponse splitPosition(SplitPositionRequest request)
            throws IOException {
        requireProvider();
        @SuppressWarnings("unchecked")
        String data = encodeFunction("splitPosition", Arrays.<Type>asList(
            new Address(request.getCollateralToken()),
            new Bytes32(hexToBytes32(request.getParentCollectionId())),
            new Bytes32(hexToBytes32(request.getConditionId())),
            new DynamicArray<>(Uint256.class, toUint256List(request.getPartition())),
            new Uint256(request.getAmount())
        ));
        String txHash = sendTransaction(contractAddress, data);
        EthGetTransactionReceipt receipt = waitForReceipt(txHash);
        return new SplitPositionResponse(
            txHash,
            receipt.getTransactionReceipt()
                .map(r -> r.getBlockNumber().longValue())
                .orElseThrow(() -> new CtfException("Block number unavailable in receipt"))
        );
    }

    /**
     * Merges outcome token pairs back into collateral on-chain.
     *
     * @param request merge parameters
     * @return transaction hash and block number
     * @throws IOException  if a network error occurs
     * @throws CtfException if the client has no provider configured
     */
    public MergePositionsResponse mergePositions(MergePositionsRequest request)
            throws IOException {
        requireProvider();
        @SuppressWarnings("unchecked")
        String data = encodeFunction("mergePositions", Arrays.<Type>asList(
            new Address(request.getCollateralToken()),
            new Bytes32(hexToBytes32(request.getParentCollectionId())),
            new Bytes32(hexToBytes32(request.getConditionId())),
            new DynamicArray<>(Uint256.class, toUint256List(request.getPartition())),
            new Uint256(request.getAmount())
        ));
        String txHash = sendTransaction(contractAddress, data);
        EthGetTransactionReceipt receipt = waitForReceipt(txHash);
        return new MergePositionsResponse(
            txHash,
            receipt.getTransactionReceipt()
                .map(r -> r.getBlockNumber().longValue())
                .orElseThrow(() -> new CtfException("Block number unavailable in receipt"))
        );
    }

    /**
     * Redeems winning outcome tokens for collateral on-chain.
     *
     * @param request redeem parameters
     * @return transaction hash and block number
     * @throws IOException  if a network error occurs
     * @throws CtfException if the client has no provider configured
     */
    public RedeemPositionsResponse redeemPositions(RedeemPositionsRequest request)
            throws IOException {
        requireProvider();
        @SuppressWarnings("unchecked")
        String data = encodeFunction("redeemPositions", Arrays.<Type>asList(
            new Address(request.getCollateralToken()),
            new Bytes32(hexToBytes32(request.getParentCollectionId())),
            new Bytes32(hexToBytes32(request.getConditionId())),
            new DynamicArray<>(Uint256.class, toUint256List(request.getIndexSets()))
        ));
        String txHash = sendTransaction(contractAddress, data);
        EthGetTransactionReceipt receipt = waitForReceipt(txHash);
        return new RedeemPositionsResponse(
            txHash,
            receipt.getTransactionReceipt()
                .map(r -> r.getBlockNumber().longValue())
                .orElseThrow(() -> new CtfException("Block number unavailable in receipt"))
        );
    }

    /**
     * Redeems positions using the NegRisk adapter on-chain.
     *
     * @param request NegRisk redeem parameters
     * @return transaction hash and block number
     * @throws IOException  if a network error occurs
     * @throws CtfException if the client was not created with NegRisk support, or has no provider
     */
    public RedeemNegRiskResponse redeemNegRisk(RedeemNegRiskRequest request)
            throws IOException {
        requireProvider();
        if (negRiskAdapterAddress == null) {
            throw new CtfException(
                "NegRisk adapter not available. Use CtfClient.forChainWithNegRisk() or "
                + "Builder.negRisk(true) to enable NegRisk support.");
        }
        @SuppressWarnings("unchecked")
        String data = encodeFunction("redeemPositions", Arrays.<Type>asList(
            new Bytes32(hexToBytes32(request.getConditionId())),
            new DynamicArray<>(Uint256.class, toUint256List(request.getAmounts()))
        ));
        String txHash = sendTransaction(negRiskAdapterAddress, data);
        EthGetTransactionReceipt receipt = waitForReceipt(txHash);
        return new RedeemNegRiskResponse(
            txHash,
            receipt.getTransactionReceipt()
                .map(r -> r.getBlockNumber().longValue())
                .orElseThrow(() -> new CtfException("Block number unavailable in receipt"))
        );
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns the chain ID this client is configured for. */
    public int getChainId() { return chainId; }

    /** Returns the CTF contract address for this chain. */
    public String getContractAddress() { return contractAddress; }

    /** Returns {@code true} if this client was created with NegRisk adapter support. */
    public boolean hasNegRisk() { return negRiskAdapterAddress != null; }

    /** Returns {@code true} if a Web3j provider is configured (required for on-chain ops). */
    public boolean hasProvider() { return web3j != null; }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void requireProvider() {
        if (web3j == null || credentials == null) {
            throw new CtfException(
                "On-chain operations require a Web3j provider and credentials. "
                + "Configure them via CtfClient.Builder.");
        }
    }

    private String sendTransaction(String to, String data) throws IOException {
        RawTransactionManager txManager = new RawTransactionManager(web3j, credentials, chainId);
        EthSendTransaction response = txManager.sendTransaction(
            gasPrice, gasLimit, to, data, BigInteger.ZERO
        );
        if (response.hasError()) {
            throw new CtfException("Transaction failed: " + response.getError().getMessage());
        }
        return response.getTransactionHash();
    }

    private EthGetTransactionReceipt waitForReceipt(String txHash) throws IOException {
        for (int i = 0; i < RECEIPT_MAX_ATTEMPTS; i++) {
            EthGetTransactionReceipt receipt = web3j.ethGetTransactionReceipt(txHash).send();
            if (receipt.getTransactionReceipt().isPresent()) {
                return receipt;
            }
            try {
                Thread.sleep(RECEIPT_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CtfException("Interrupted while waiting for transaction receipt");
            }
        }
        throw new CtfException("Transaction receipt not found after " + RECEIPT_MAX_ATTEMPTS
            + " attempts for tx: " + txHash);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String encodeFunction(String name, List<Type> inputs) {
        Function function = new Function(
            name, inputs, Collections.<TypeReference<?>>emptyList());
        return FunctionEncoder.encode(function);
    }

    private static List<Uint256> toUint256List(List<BigInteger> values) {
        return values.stream().map(Uint256::new).collect(Collectors.toList());
    }

    /** Decode a 0x-prefixed hex address into a raw 20-byte array. */
    static byte[] addressToBytes20(String address) {
        byte[] raw = Numeric.hexStringToByteArray(address);
        if (raw.length == 20) return raw;
        // Pad / trim to 20 bytes (right-aligned)
        byte[] out = new byte[20];
        int srcOffset = Math.max(0, raw.length - 20);
        int dstOffset = Math.max(0, 20 - raw.length);
        System.arraycopy(raw, srcOffset, out, dstOffset, Math.min(raw.length, 20));
        return out;
    }

    /** Decode a 0x-prefixed bytes32 hex string into a 32-byte array. */
    static byte[] hexToBytes32(String hex) {
        byte[] raw = Numeric.hexStringToByteArray(hex);
        if (raw.length == 32) return raw;
        byte[] out = new byte[32];
        int srcOffset = Math.max(0, raw.length - 32);
        int dstOffset = Math.max(0, 32 - raw.length);
        System.arraycopy(raw, srcOffset, out, dstOffset, Math.min(raw.length, 32));
        return out;
    }

    /** Encode a BigInteger as a 32-byte big-endian (uint256) array. */
    static byte[] toBigEndian32(BigInteger value) {
        byte[] raw = value.toByteArray();
        // toByteArray() may include a leading 0x00 sign byte
        if (raw.length == 33 && raw[0] == 0) raw = Arrays.copyOfRange(raw, 1, 33);
        byte[] out = new byte[32];
        System.arraycopy(raw, 0, out, 32 - raw.length, Math.min(raw.length, 32));
        return out;
    }

    /** XOR two 32-byte arrays. */
    static byte[] xor32(byte[] a, byte[] b) {
        byte[] result = new byte[32];
        for (int i = 0; i < 32; i++) {
            result[i] = (byte) (a[i] ^ b[i]);
        }
        return result;
    }

    /** Concatenate arbitrary byte arrays. */
    static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) total += p.length;
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /** Returns a new builder for {@link CtfClient}. */
    public static Builder builder() { return new Builder(); }

    /** Builder for {@link CtfClient}. */
    public static final class Builder {

        private int chainId = 137;
        private boolean negRisk = false;
        private Web3j web3j;
        private Credentials credentials;
        private BigInteger gasPrice;
        private BigInteger gasLimit;

        // resolved from chainId
        private String contractAddress;
        private String negRiskAdapterAddress;

        public Builder chainId(int chainId) { this.chainId = chainId; return this; }

        /** Enable NegRisk adapter support. */
        public Builder negRisk(boolean negRisk) { this.negRisk = negRisk; return this; }

        /** Supply a Web3j provider for on-chain operations. */
        public Builder web3j(Web3j web3j) { this.web3j = web3j; return this; }

        /** Supply signing credentials for on-chain operations. */
        public Builder credentials(Credentials credentials) {
            this.credentials = credentials; return this;
        }

        /** Override the default gas price (50 Gwei). */
        public Builder gasPrice(BigInteger gasPrice) { this.gasPrice = gasPrice; return this; }

        /** Override the default gas limit (300 000). */
        public Builder gasLimit(BigInteger gasLimit) { this.gasLimit = gasLimit; return this; }

        /**
         * Builds a {@link CtfClient}.
         *
         * @throws CtfException if the chain ID is not supported
         */
        public CtfClient build() {
            contractAddress = CTF_CONTRACT_ADDRESS.get(chainId);
            if (contractAddress == null) {
                throw new CtfException(
                    "CTF contract configuration not found for chain ID " + chainId
                    + ". Supported chains: " + CTF_CONTRACT_ADDRESS.keySet());
            }
            if (negRisk) {
                negRiskAdapterAddress = NEG_RISK_ADAPTER_ADDRESS.get(chainId);
                if (negRiskAdapterAddress == null) {
                    throw new CtfException(
                        "NegRisk adapter configuration not found for chain ID " + chainId);
                }
            }
            return new CtfClient(this);
        }
    }
}
