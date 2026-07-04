package com.polymarket.util;

import com.polymarket.model.OrderData;
import com.polymarket.model.OrderDataV2;
import com.polymarket.model.Side;
import com.polymarket.model.SignatureType;
import com.polymarket.model.SignedOrder;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Map;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

/**
 * Standalone low-level order builder for Polymarket orders.
 *
 * <p>Unlike {@code OrderBuilder}, this class does <em>not</em> require a {@code PolymarketClient}.
 * It accepts pre-calculated raw amounts (maker/taker in USDC × 10^6 units) and directly produces
 * a signed {@link SignedOrder}. This is the Java equivalent of Python's {@code py-order-utils}.
 *
 * <p>EIP-712 implementation uses raw byte ABI encoding for auditability, as described in the
 * {@code java-order-utils/BaseBuilder.java} reference:
 * <pre>
 *   domainHash = keccak256(typeHash ‖ keccak256(name) ‖ keccak256(version) ‖ chainId[32] ‖ address[32])
 *   orderHash  = keccak256(orderTypeHash ‖ salt[32] ‖ maker[32] ‖ signer[32] ‖ taker[32] ‖ tokenId[32]
 *                          ‖ makerAmount[32] ‖ takerAmount[32] ‖ expiration[32] ‖ nonce[32]
 *                          ‖ feeRateBps[32] ‖ side[32] ‖ signatureType[32])
 *   msgHash    = keccak256(0x1901 ‖ domainHash ‖ orderHash)
 * </pre>
 *
 * <p>Salt is always masked to IEEE 754 float64 safe integer range (≤ 2^53 − 1) to match the
 * Rust SDK ({@code rs-clob-client/src/clob/order_builder.rs: fn to_ieee_754_int}).
 */
public final class OrderUtils {

    private static final String ZERO_ADDRESS =
        "0x0000000000000000000000000000000000000000";

    // Contract addresses by chain ID — same as OrderBuilder
    private static final Map<Integer, ContractAddresses> CONTRACTS = Map.of(
        137,
        new ContractAddresses(
            "0x4bFb41d5B3570DeFd03C39a9A4D8dE6Bd8B8982E",
            "0xC5d563A36AE78145C45a50134d48A1215220f80a"
        ),
        80002,
        new ContractAddresses(
            "0xdFE02Eb6733538f8Ea35D585af8DE5958AD99E40",
            "0xC5d563A36AE78145C45a50134d48A1215220f80a"
        )
    );

    // V2 exchange + neg-risk-V2 are identical across chains 137 and 80002 (PMK-003).
    private static final String EXCHANGE_V2 =
        "0xE111180000d2663C0091e4f400237545B87B996B";
    private static final String NEG_RISK_EXCHANGE_V2 =
        "0xe2222d279d744050d28e00520010520000310F59";
    private static final Map<Integer, ContractAddressesV2> CONTRACTS_V2 = Map.of(
        137, new ContractAddressesV2(EXCHANGE_V2, NEG_RISK_EXCHANGE_V2),
        80002, new ContractAddressesV2(EXCHANGE_V2, NEG_RISK_EXCHANGE_V2)
    );

    private static final String BYTES32_ZERO =
        "0x0000000000000000000000000000000000000000000000000000000000000000";

    // EIP-712 type hashes — match clob-client and java-order-utils/BaseBuilder.java
    private static final byte[] DOMAIN_TYPE_HASH = Hash.sha3(
        "EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)"
            .getBytes(StandardCharsets.UTF_8)
    );

    private static final byte[] ORDER_TYPE_HASH = Hash.sha3(
        (
            "Order(uint256 salt,address maker,address signer,address taker,uint256 tokenId,"
                + "uint256 makerAmount,uint256 takerAmount,uint256 expiration,uint256 nonce,"
                + "uint256 feeRateBps,uint8 side,uint8 signatureType)"
        ).getBytes(StandardCharsets.UTF_8)
    );

    // V2 EIP-712 struct type string — ground truth: rs-clob-client/src/clob/client.rs ORDER_TYPE_STRING.
    static final String ORDER_TYPE_STRING_V2 =
        "Order(uint256 salt,address maker,address signer,uint256 tokenId,"
            + "uint256 makerAmount,uint256 takerAmount,uint8 side,uint8 signatureType,"
            + "uint256 timestamp,bytes32 metadata,bytes32 builder)";
    static final byte[] ORDER_TYPE_HASH_V2 = Hash.sha3(ORDER_TYPE_STRING_V2.getBytes(StandardCharsets.UTF_8));

    // POLY_1271 (EIP-1271 DepositWallet) wrapped-signature constants — ground truth:
    // rs-clob-client/src/clob/client.rs SOLADY_TYPE_STRING / DEPOSIT_WALLET_*.
    private static final String SOLADY_TYPE_STRING =
        "TypedDataSign(Order contents,string name,string version,uint256 chainId,"
            + "address verifyingContract,bytes32 salt)"
            + "Order(uint256 salt,address maker,address signer,uint256 tokenId,"
            + "uint256 makerAmount,uint256 takerAmount,uint8 side,uint8 signatureType,"
            + "uint256 timestamp,bytes32 metadata,bytes32 builder)";
    private static final String DEPOSIT_WALLET_NAME = "DepositWallet";
    private static final String DEPOSIT_WALLET_VERSION = "1";
    private static final String EXCHANGE_NAME = "Polymarket CTF Exchange";

    private final Credentials credentials;
    private final int chainId;
    private final SignatureType defaultSignatureType;
    private final String funderAddress;
    private final SecureRandom random = new SecureRandom();

    public OrderUtils(Credentials credentials, int chainId) {
        this(credentials, chainId, SignatureType.EOA, null);
    }

    public OrderUtils(
        Credentials credentials,
        int chainId,
        SignatureType signatureType,
        String funderAddress
    ) {
        if (credentials == null) throw new IllegalArgumentException("credentials is required");
        if (!CONTRACTS.containsKey(chainId) && !CONTRACTS_V2.containsKey(chainId)) {
            throw new IllegalArgumentException("Unsupported chain ID: " + chainId);
        }
        this.credentials = credentials;
        this.chainId = chainId;
        this.defaultSignatureType = signatureType != null ? signatureType : SignatureType.EOA;
        this.funderAddress = funderAddress;
    }

    /**
     * Returns the exchange (verifying contract) address for the given chain and neg-risk setting.
     *
     * @param chainId the chain ID (137 = Polygon Mainnet, 80002 = Amoy Testnet)
     * @param negRisk {@code true} to use the neg-risk exchange contract
     * @return the contract address as a hex string
     * @throws IllegalArgumentException if the chain ID is not supported
     */
    public static String exchangeAddress(int chainId, boolean negRisk) {
        ContractAddresses addrs = CONTRACTS.get(chainId);
        if (addrs == null) {
            throw new IllegalArgumentException("Unsupported chain ID: " + chainId);
        }
        return negRisk ? addrs.negRisk() : addrs.exchange();
    }

    /**
     * Returns the V2 exchange (verifying contract) address for the given chain and neg-risk
     * setting (PMK-003). V2 addresses are identical across chains 137 and 80002.
     */
    public static String exchangeAddressV2(int chainId, boolean negRisk) {
        ContractAddressesV2 addrs = CONTRACTS_V2.get(chainId);
        if (addrs == null) {
            throw new IllegalArgumentException("Unsupported chain ID: " + chainId);
        }
        return negRisk ? addrs.negRiskExchangeV2() : addrs.exchangeV2();
    }

    /**
     * Builds a signed order from raw order data in a single step.
     *
     * @param data pre-validated order data with raw blockchain amounts
     * @return a fully signed {@link SignedOrder} ready to post
     */
    public SignedOrder buildSignedOrder(OrderData data) {
        validate(data);

        SignatureType sigType = data.getSignatureType() != null
            ? data.getSignatureType()
            : defaultSignatureType;

        String maker = getMakerAddress(sigType);
        String signer = getSigner(data);
        String taker = data.getTaker() != null ? data.getTaker() : ZERO_ADDRESS;
        BigInteger nonce = data.getNonce() != null ? data.getNonce() : BigInteger.ZERO;
        BigInteger expiration = data.getExpiration() != null ? data.getExpiration() : BigInteger.ZERO;
        BigInteger feeRateBps = data.getFeeRateBps();

        // Mask salt to IEEE 754 safe integer range (≤ 2^53 − 1)
        long salt = random.nextLong() & ((1L << 53) - 1);

        boolean negRisk = false; // OrderData has no neg-risk field; defaults to false
        String exchange = exchangeAddress(chainId, negRisk);

        String signature = signOrder(
            exchange,
            salt,
            maker,
            signer,
            taker,
            data.getTokenId(),
            data.getMakerAmount(),
            data.getTakerAmount(),
            expiration,
            nonce,
            feeRateBps,
            data.getSide(),
            sigType
        );

        return SignedOrder.builder()
            .salt(salt)
            .maker(maker)
            .signer(signer)
            .taker(taker)
            .tokenId(data.getTokenId())
            .makerAmount(data.getMakerAmount().toString())
            .takerAmount(data.getTakerAmount().toString())
            .expiration(expiration.toString())
            .nonce(nonce.toString())
            .feeRateBps(feeRateBps.toString())
            .side(data.getSide())
            .signatureType(sigType)
            .signature(signature)
            .build();
    }

    /**
     * Signs a fully specified order with a given exchange address and salt.
     *
     * @param exchangeAddress the verifying contract address
     * @param salt the order salt (should already be IEEE 754 masked)
     * @param maker the maker address
     * @param signer the signer address
     * @param taker the taker address
     * @param tokenId the CTF token ID
     * @param makerAmount maker amount in raw units
     * @param takerAmount taker amount in raw units
     * @param expiration expiration timestamp (0 = no expiry)
     * @param nonce order nonce
     * @param feeRateBps fee rate in basis points
     * @param side BUY or SELL
     * @param sigType signature type
     * @return hex-encoded ECDSA signature
     */
    public String signOrder(
        String exchangeAddress,
        long salt,
        String maker,
        String signer,
        String taker,
        String tokenId,
        BigInteger makerAmount,
        BigInteger takerAmount,
        BigInteger expiration,
        BigInteger nonce,
        BigInteger feeRateBps,
        Side side,
        SignatureType sigType
    ) {
        byte[] domainHash = buildDomainHash(exchangeAddress);
        byte[] orderHash = buildOrderHash(
            salt,
            maker,
            signer,
            taker,
            new BigInteger(tokenId),
            makerAmount,
            takerAmount,
            expiration,
            nonce,
            feeRateBps,
            side,
            sigType
        );

        byte[] msgHash = buildMessageHash(domainHash, orderHash);
        Sign.SignatureData sig = Sign.signMessage(msgHash, credentials.getEcKeyPair(), false);
        return toHexSignature(sig);
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    private void validate(OrderData data) {
        if (data.getTokenId() == null || data.getTokenId().isBlank()) {
            throw new IllegalArgumentException("tokenId is required");
        }
        if (data.getSide() == null) {
            throw new IllegalArgumentException("side is required");
        }
        if (data.getMakerAmount() == null) {
            throw new IllegalArgumentException("makerAmount is required");
        }
        if (data.getTakerAmount() == null) {
            throw new IllegalArgumentException("takerAmount is required");
        }
        if (data.getFeeRateBps() == null) {
            throw new IllegalArgumentException("feeRateBps is required");
        }
    }

    private String getMakerAddress(SignatureType sigType) {
        if (sigType == SignatureType.POLY_PROXY || sigType == SignatureType.POLY_GNOSIS_SAFE) {
            if (funderAddress != null && !funderAddress.isBlank()) {
                return funderAddress;
            }
        }
        return credentials.getAddress();
    }

    private String getSigner(OrderData data) {
        if (data.getSigner() != null && !data.getSigner().isBlank()) {
            return data.getSigner();
        }
        return credentials.getAddress();
    }

    /**
     * Computes the EIP-712 domain separator hash.
     *
     * <pre>
     * domainHash = keccak256(typeHash ‖ keccak256("Polymarket CTF Exchange")
     *                        ‖ keccak256("1") ‖ chainId[32] ‖ verifyingContract[32])
     * </pre>
     */
    private byte[] buildDomainHash(String verifyingContract) {
        byte[] nameHash = Hash.sha3("Polymarket CTF Exchange".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] versionHash = Hash.sha3("1".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        byte[] buf = new byte[32 * 5]; // typeHash + name + version + chainId + address
        System.arraycopy(DOMAIN_TYPE_HASH, 0, buf, 0, 32);
        System.arraycopy(nameHash, 0, buf, 32, 32);
        System.arraycopy(versionHash, 0, buf, 64, 32);
        copyPadded(BigInteger.valueOf(chainId), buf, 96);
        copyAddress(verifyingContract, buf, 128);

        return Hash.sha3(buf);
    }

    /**
     * Computes the EIP-712 struct hash for an Order.
     *
     * <pre>
     * orderHash = keccak256(orderTypeHash ‖ salt[32] ‖ maker[32] ‖ signer[32] ‖ taker[32]
     *                       ‖ tokenId[32] ‖ makerAmount[32] ‖ takerAmount[32]
     *                       ‖ expiration[32] ‖ nonce[32] ‖ feeRateBps[32]
     *                       ‖ side[32] ‖ signatureType[32])
     * </pre>
     */
    private byte[] buildOrderHash(
        long salt,
        String maker,
        String signer,
        String taker,
        BigInteger tokenId,
        BigInteger makerAmount,
        BigInteger takerAmount,
        BigInteger expiration,
        BigInteger nonce,
        BigInteger feeRateBps,
        Side side,
        SignatureType sigType
    ) {
        byte[] buf = new byte[32 * 13]; // typeHash + 12 fields
        int offset = 0;

        System.arraycopy(ORDER_TYPE_HASH, 0, buf, offset, 32);
        offset += 32;

        copyPadded(BigInteger.valueOf(salt), buf, offset);       offset += 32;
        copyAddress(maker, buf, offset);                         offset += 32;
        copyAddress(signer, buf, offset);                        offset += 32;
        copyAddress(taker, buf, offset);                         offset += 32;
        copyPadded(tokenId, buf, offset);                        offset += 32;
        copyPadded(makerAmount, buf, offset);                    offset += 32;
        copyPadded(takerAmount, buf, offset);                    offset += 32;
        copyPadded(expiration, buf, offset);                     offset += 32;
        copyPadded(nonce, buf, offset);                          offset += 32;
        copyPadded(feeRateBps, buf, offset);                     offset += 32;
        copyPadded(BigInteger.valueOf(side == Side.BUY ? 0 : 1), buf, offset); offset += 32;
        copyPadded(BigInteger.valueOf(sigType.getValue()), buf, offset);

        return Hash.sha3(buf);
    }

    /**
     * Computes the final EIP-712 message hash: {@code keccak256(0x1901 ‖ domainHash ‖ orderHash)}.
     */
    private byte[] buildMessageHash(byte[] domainHash, byte[] orderHash) {
        byte[] buf = new byte[2 + 32 + 32];
        buf[0] = 0x19;
        buf[1] = 0x01;
        System.arraycopy(domainHash, 0, buf, 2, 32);
        System.arraycopy(orderHash, 0, buf, 34, 32);
        return Hash.sha3(buf);
    }

    /** Copies a BigInteger as a 32-byte big-endian padded value into {@code buf} at {@code offset}. */
    private static void copyPadded(BigInteger value, byte[] buf, int offset) {
        byte[] bytes = Numeric.toBytesPadded(value, 32);
        System.arraycopy(bytes, 0, buf, offset, 32);
    }

    /** Copies a hex Ethereum address into {@code buf} at {@code offset} as 32-byte left-zero-padded. */
    private static void copyAddress(String hexAddress, byte[] buf, int offset) {
        byte[] addrBytes = Numeric.hexStringToByteArray(hexAddress);
        // Addresses are 20 bytes, padded to 32 with 12 leading zeros
        int padding = 32 - addrBytes.length;
        System.arraycopy(addrBytes, 0, buf, offset + padding, addrBytes.length);
    }

    private static String toHexSignature(Sign.SignatureData sig) {
        byte[] combined = new byte[65];
        System.arraycopy(sig.getR(), 0, combined, 0, 32);
        System.arraycopy(sig.getS(), 0, combined, 32, 32);
        combined[64] = sig.getV()[0];
        return Numeric.toHexString(combined);
    }

    // ── V2 order signing (PMK-005 / PMK-006) ─────────────────────────────────
    //
    // Ground truth: rs-clob-client/src/clob/client.rs (sign) and order_builder.rs.
    //   domainSeparator = keccak256(EIP712Domain typeHash ‖ keccak256(name) ‖ keccak256("2")
    //                              ‖ chainId[32] ‖ verifyingContract[32])
    //   structHash      = keccak256(ORDER_TYPE_HASH_V2 ‖ salt[32] ‖ maker[32] ‖ signer[32]
    //                              ‖ tokenId[32] ‖ makerAmount[32] ‖ takerAmount[32]
    //                              ‖ side[32] ‖ signatureType[32] ‖ timestamp[32]
    //                              ‖ metadata[32] ‖ builder[32])
    //   eip712Hash      = keccak256(0x1901 ‖ domainSeparator ‖ structHash)
    //
    // POLY_1271 (EIP-1271 DepositWallet) wraps the inner ECDSA signature; see signPoly1271.

    private static final byte[] NAME_HASH = Hash.sha3(EXCHANGE_NAME.getBytes(StandardCharsets.UTF_8));
    private static final byte[] VERSION_V2_HASH = Hash.sha3("2".getBytes(StandardCharsets.UTF_8));
    private static final byte[] DEPOSIT_WALLET_NAME_HASH =
        Hash.sha3(DEPOSIT_WALLET_NAME.getBytes(StandardCharsets.UTF_8));
    private static final byte[] DEPOSIT_WALLET_VERSION_HASH =
        Hash.sha3(DEPOSIT_WALLET_VERSION.getBytes(StandardCharsets.UTF_8));
    private static final byte[] SOLADY_TYPE_HASH =
        Hash.sha3(SOLADY_TYPE_STRING.getBytes(StandardCharsets.UTF_8));

    /**
     * Builds and signs a V2 order (PMK-005/006). Produces the V2 EIP-712 signature against the
     * V2 exchange contract; for {@link SignatureType#POLY_1271} a funder (deposit wallet) address
     * is required and the wrapped EIP-1271 signature is produced. Returns a version-tagged
     * {@link SignedOrder} ({@code version=2}) that serializes to the V2 wire shape.
     *
     * @param data    V2 order input. {@code salt} must already be IEEE-754 masked by the caller.
     * @param negRisk {@code true} to sign against the neg-risk V2 exchange contract
     * @return a {@link SignedOrder} tagged {@code version=2}, ready to package in a {@link
     *     com.polymarket.model.PostOrderPayload}
     */
    public SignedOrder buildSignedOrderV2(OrderDataV2 data, boolean negRisk) {
        validateV2(data);

        SignatureType sigType = data.getSignatureType() != null
            ? data.getSignatureType() : defaultSignatureType;

        if (sigType == SignatureType.POLY_1271
            && (funderAddress == null || funderAddress.isBlank())) {
            throw new IllegalArgumentException(
                "A deposit wallet funder address is required with a POLY_1271 signature type");
        }

        String maker = resolveMaker(data, sigType);
        String signer = (sigType == SignatureType.POLY_1271)
            ? maker
            : (data.getSigner() != null && !data.getSigner().isBlank()
                ? data.getSigner() : credentials.getAddress());
        data = data.toBuilder().maker(maker).signer(signer).build();

        String verifyingContract = exchangeAddressV2(chainId, negRisk);
        byte[] domainHash = buildV2DomainHash(verifyingContract);
        byte[] structHash = buildV2StructHash(data, sigType);

        String signature = sigType == SignatureType.POLY_1271
            ? signPoly1271(domainHash, structHash, maker)
            : ecdsaSign(domainHash, structHash);

        return SignedOrder.v2Builder()
            .salt(data.getSalt().longValueExact())
            .maker(maker)
            .signer(signer)
            .tokenId(data.getTokenId())
            .makerAmount(data.getMakerAmount().toString())
            .takerAmount(data.getTakerAmount().toString())
            .side(data.getSide())
            .signatureType(sigType)
            .timestamp(data.getTimestamp().toString())
            .metadata(data.getMetadata())
            .builderCode(data.getBuilder())
            .signature(signature)
            .build();
    }

    /** Computes the V2 EIP-712 domain separator (version "2", V2 verifying contract). */
    public byte[] buildV2DomainHash(String verifyingContract) {
        byte[] buf = new byte[32 * 5];
        System.arraycopy(DOMAIN_TYPE_HASH, 0, buf, 0, 32);
        System.arraycopy(NAME_HASH, 0, buf, 32, 32);
        System.arraycopy(VERSION_V2_HASH, 0, buf, 64, 32);
        copyPadded(BigInteger.valueOf(chainId), buf, 96);
        copyAddress(verifyingContract, buf, 128);
        return Hash.sha3(buf);
    }

    /** Computes the V2 struct hash (timestamp/metadata/builder included; V1-only fields absent). */
    public byte[] buildV2StructHash(OrderDataV2 data, SignatureType sigType) {
        byte[] buf = new byte[32 * 12]; // typeHash + 11 fields
        int offset = 0;
        System.arraycopy(ORDER_TYPE_HASH_V2, 0, buf, offset, 32); offset += 32;
        copyPadded(data.getSalt(), buf, offset); offset += 32;
        copyAddress(data.getMaker(), buf, offset); offset += 32;
        copyAddress(
            data.getSigner() != null && !data.getSigner().isBlank() ? data.getSigner() : data.getMaker(),
            buf, offset); offset += 32;
        copyPadded(new BigInteger(data.getTokenId()), buf, offset); offset += 32;
        copyPadded(data.getMakerAmount(), buf, offset); offset += 32;
        copyPadded(data.getTakerAmount(), buf, offset); offset += 32;
        copyPadded(BigInteger.valueOf(data.getSide() == Side.BUY ? 0 : 1), buf, offset); offset += 32;
        copyPadded(BigInteger.valueOf(sigType.getValue()), buf, offset); offset += 32;
        copyPadded(data.getTimestamp(), buf, offset); offset += 32;
        copyBytes32(data.getMetadata(), buf, offset); offset += 32;
        copyBytes32(data.getBuilder(), buf, offset);
        return Hash.sha3(buf);
    }

    /**
     * Computes the POLY_1271 (EIP-1271 DepositWallet) wrapped signature (PMK-006).
     *
     * <p>Layout (ground truth: {@code rs-clob-client/src/clob/client.rs: sign_poly1271_order}):
     * wrapped = "0x"
     *   ‖ hex(innerEcdsa 65 bytes)
     *   ‖ hex(appDomainSeparator 32 bytes)
     *   ‖ hex(contentsHash 32 bytes)        // = V2 struct hash
     *   ‖ hex(ORDER_TYPE_STRING_V2 bytes)
     *   ‖ hex(u16 big-endian length of ORDER_TYPE_STRING_V2)
     */
    public String signPoly1271(byte[] appDomainSeparator, byte[] contentsHash, String signerAddress) {
        // typed_data_sign_struct_hash = keccak256(abi_encode of the 7-field Solady tuple)
        byte[] tuple = new byte[32 * 7];
        int offset = 0;
        System.arraycopy(SOLADY_TYPE_HASH, 0, tuple, offset, 32); offset += 32;
        System.arraycopy(contentsHash, 0, tuple, offset, 32); offset += 32;
        System.arraycopy(DEPOSIT_WALLET_NAME_HASH, 0, tuple, offset, 32); offset += 32;
        System.arraycopy(DEPOSIT_WALLET_VERSION_HASH, 0, tuple, offset, 32); offset += 32;
        copyPadded(BigInteger.valueOf(chainId), tuple, offset); offset += 32;
        copyAddress(signerAddress, tuple, offset); offset += 32;
        // B256::ZERO salt → 32 zero bytes (already zero)
        byte[] typedDataSignStructHash = Hash.sha3(tuple);

        byte[] digestInput = new byte[66];
        digestInput[0] = 0x19;
        digestInput[1] = 0x01;
        System.arraycopy(appDomainSeparator, 0, digestInput, 2, 32);
        System.arraycopy(typedDataSignStructHash, 0, digestInput, 34, 32);
        byte[] digest = Hash.sha3(digestInput);

        Sign.SignatureData inner = Sign.signMessage(digest, credentials.getEcKeyPair(), false);
        byte[] innerBytes = new byte[65];
        System.arraycopy(inner.getR(), 0, innerBytes, 0, 32);
        System.arraycopy(inner.getS(), 0, innerBytes, 32, 32);
        innerBytes[64] = inner.getV()[0];

        StringBuilder sb = new StringBuilder("0x");
        appendHex(sb, innerBytes);
        appendHex(sb, appDomainSeparator);
        appendHex(sb, contentsHash);
        appendHex(sb, ORDER_TYPE_STRING_V2.getBytes(StandardCharsets.UTF_8));
        int typeLen = ORDER_TYPE_STRING_V2.length();
        sb.append(String.format("%02x", (typeLen >> 8) & 0xff));
        sb.append(String.format("%02x", typeLen & 0xff));
        return sb.toString();
    }

    private String ecdsaSign(byte[] domainHash, byte[] structHash) {
        byte[] msgHash = buildMessageHash(domainHash, structHash);
        Sign.SignatureData sig = Sign.signMessage(msgHash, credentials.getEcKeyPair(), false);
        return toHexSignature(sig);
    }

    private void validateV2(OrderDataV2 data) {
        if (data.getTokenId() == null || data.getTokenId().isBlank()) {
            throw new IllegalArgumentException("tokenId is required");
        }
        if (data.getSide() == null) {
            throw new IllegalArgumentException("side is required");
        }
        if (data.getMakerAmount() == null) {
            throw new IllegalArgumentException("makerAmount is required");
        }
        if (data.getTakerAmount() == null) {
            throw new IllegalArgumentException("takerAmount is required");
        }
        if (data.getSalt() == null) {
            throw new IllegalArgumentException("salt is required");
        }
        if (data.getTimestamp() == null) {
            throw new IllegalArgumentException("timestamp is required");
        }
    }

    private String resolveMaker(OrderDataV2 data, SignatureType sigType) {
        if (sigType == SignatureType.POLY_PROXY
            || sigType == SignatureType.POLY_GNOSIS_SAFE
            || sigType == SignatureType.POLY_1271) {
            if (data.getMaker() != null && !data.getMaker().isBlank()) {
                return data.getMaker();
            }
            if (funderAddress != null && !funderAddress.isBlank()) {
                return funderAddress;
            }
        }
        return data.getMaker() != null && !data.getMaker().isBlank()
            ? data.getMaker() : credentials.getAddress();
    }

    private static void copyBytes32(String hexOrZero, byte[] buf, int offset) {
        String hex = (hexOrZero == null || hexOrZero.isBlank()) ? BYTES32_ZERO : hexOrZero;
        byte[] bytes = Numeric.hexStringToByteArray(hex);
        if (bytes.length != 32) {
            throw new IllegalArgumentException("expected bytes32 (32 bytes), got " + bytes.length);
        }
        System.arraycopy(bytes, 0, buf, offset, 32);
    }

    private static void appendHex(StringBuilder sb, byte[] bytes) {
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
    }

    private record ContractAddresses(String exchange, String negRisk) {}
    private record ContractAddressesV2(String exchangeV2, String negRiskExchangeV2) {}
}
