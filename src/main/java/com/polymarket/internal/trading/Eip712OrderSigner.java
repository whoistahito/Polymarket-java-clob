package com.polymarket.internal.trading;

import com.polymarket.authentication.SigningIdentity;
import com.polymarket.markets.AssetId;
import com.polymarket.markets.MarketRules;
import com.polymarket.markets.PositionId;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
import com.polymarket.markets.TokenId;
import com.polymarket.trading.OrderSigner;
import com.polymarket.trading.Side;
import com.polymarket.trading.SignedOrder;
import com.polymarket.trading.SigningContext;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

/**
 * Ground truth: docs.polymarket.com/trading/place-orders (V2) and /trading/combos/market-makers
 * (V3), pinned byte-for-byte in {@code src/test/resources/protocol/signing-vectors.json}.
 */
public final class Eip712OrderSigner implements OrderSigner {

    private static final int CHAIN_ID = 137;
    private static final String EXCHANGE_NAME = "Polymarket CTF Exchange";
    private static final String EXCHANGE_V2 = "0xE111180000d2663C0091e4f400237545B87B996B";
    private static final String NEG_RISK_EXCHANGE_V2 = "0xe2222d279d744050d28e00520010520000310F59";
    private static final String EXCHANGE_V3 = "0xe3333700cA9d93003F00f0F71f8515005F6c00Aa";
    private static final int DEPOSIT_WALLET_SIGNATURE_TYPE = 3;
    private static final String BYTES32_ZERO = "0x" + "0".repeat(64);

    private static final String ORDER_TYPE_STRING =
            "Order(uint256 salt,address maker,address signer,uint256 tokenId,uint256 makerAmount,"
                    + "uint256 takerAmount,uint8 side,uint8 signatureType,uint256 timestamp,"
                    + "bytes32 metadata,bytes32 builder)";
    private static final byte[] ORDER_TYPE_HASH =
            Hash.sha3(ORDER_TYPE_STRING.getBytes(StandardCharsets.UTF_8));

    private static final String DOMAIN_TYPE_STRING =
            "EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)";
    private static final byte[] DOMAIN_TYPE_HASH =
            Hash.sha3(DOMAIN_TYPE_STRING.getBytes(StandardCharsets.UTF_8));
    private static final byte[] EXCHANGE_NAME_HASH =
            Hash.sha3(EXCHANGE_NAME.getBytes(StandardCharsets.UTF_8));

    // ERC-7739 (Solady) wrapping for Deposit Wallet signatures.
    private static final String DEPOSIT_WALLET_NAME = "DepositWallet";
    private static final String DEPOSIT_WALLET_VERSION = "1";
    private static final String SOLADY_TYPE_STRING =
            "TypedDataSign(Order contents,string name,string version,uint256 chainId,"
                    + "address verifyingContract,bytes32 salt)" + ORDER_TYPE_STRING;
    private static final byte[] SOLADY_TYPE_HASH =
            Hash.sha3(SOLADY_TYPE_STRING.getBytes(StandardCharsets.UTF_8));
    private static final byte[] DEPOSIT_WALLET_NAME_HASH =
            Hash.sha3(DEPOSIT_WALLET_NAME.getBytes(StandardCharsets.UTF_8));
    private static final byte[] DEPOSIT_WALLET_VERSION_HASH =
            Hash.sha3(DEPOSIT_WALLET_VERSION.getBytes(StandardCharsets.UTF_8));

    @Override
    public SignedOrder sign(AssetId asset, Side side, PusdAmount pusdLeg,
            ShareQuantity shareLeg, MarketRules rules, SigningContext context) {
        Objects.requireNonNull(asset, "asset");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(pusdLeg, "pusdLeg");
        Objects.requireNonNull(shareLeg, "shareLeg");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(context, "context");

        String version = switch (asset) {
            case TokenId ignored -> "2";
            case PositionId ignored -> "3";
        };
        String verifyingContract = switch (asset) {
            case TokenId ignored -> rules.negativeRisk() ? NEG_RISK_EXCHANGE_V2 : EXCHANGE_V2;
            case PositionId ignored -> EXCHANGE_V3;
        };
        long timestamp = asset instanceof TokenId
                ? context.timestamp().toEpochMilli()
                : context.timestamp().getEpochSecond();

        long makerAmount = side == Side.BUY ? pusdLeg.baseUnits() : shareLeg.baseUnits();
        long takerAmount = side == Side.BUY ? shareLeg.baseUnits() : pusdLeg.baseUnits();
        String metadata = context.metadata().orElse(BYTES32_ZERO);
        String builder = context.builder().orElse(BYTES32_ZERO);
        SigningIdentity identity = context.identity();

        byte[] domainHash = domainHash(version, verifyingContract);
        byte[] structHash = orderStructHash(context.salt(), identity.tradingWallet(), identity.accountSigner(),
                asset.value(), makerAmount, takerAmount, side, identity.signatureType(),
                timestamp, metadata, builder);

        byte[] digest = identity.signatureType() == DEPOSIT_WALLET_SIGNATURE_TYPE
                ? depositWalletDigest(domainHash, structHash, identity.tradingWallet())
                : eip712Digest(domainHash, structHash);

        String signature = context.localSigner().sign(digest);

        return new SignedOrder(context.salt(), identity.tradingWallet(),
                identity.accountSigner(), asset, side, identity.signatureType(), makerAmount, takerAmount,
                timestamp, metadata, builder, signature);
    }

    private static byte[] domainHash(String version, String verifyingContract) {
        byte[] buf = new byte[32 * 5];
        System.arraycopy(DOMAIN_TYPE_HASH, 0, buf, 0, 32);
        System.arraycopy(EXCHANGE_NAME_HASH, 0, buf, 32, 32);
        System.arraycopy(Hash.sha3(version.getBytes(StandardCharsets.UTF_8)), 0, buf, 64, 32);
        copyPadded(BigInteger.valueOf(CHAIN_ID), buf, 96);
        copyAddress(verifyingContract, buf, 128);
        return Hash.sha3(buf);
    }

    private static byte[] orderStructHash(long salt, String maker, String signer, String tokenId,
            long makerAmount, long takerAmount, Side side, int signatureType, long timestamp,
            String metadata, String builder) {
        byte[] buf = new byte[32 * 12];
        int offset = 0;
        System.arraycopy(ORDER_TYPE_HASH, 0, buf, offset, 32);
        offset += 32;
        copyPadded(BigInteger.valueOf(salt), buf, offset);
        offset += 32;
        copyAddress(maker, buf, offset);
        offset += 32;
        copyAddress(signer, buf, offset);
        offset += 32;
        copyPadded(new BigInteger(tokenId), buf, offset);
        offset += 32;
        copyPadded(BigInteger.valueOf(makerAmount), buf, offset);
        offset += 32;
        copyPadded(BigInteger.valueOf(takerAmount), buf, offset);
        offset += 32;
        copyPadded(BigInteger.valueOf(side.wireValue()), buf, offset);
        offset += 32;
        copyPadded(BigInteger.valueOf(signatureType), buf, offset);
        offset += 32;
        copyPadded(BigInteger.valueOf(timestamp), buf, offset);
        offset += 32;
        copyBytes32(metadata, buf, offset);
        offset += 32;
        copyBytes32(builder, buf, offset);
        return Hash.sha3(buf);
    }

    private static byte[] eip712Digest(byte[] domainHash, byte[] structHash) {
        byte[] buf = new byte[2 + 32 + 32];
        buf[0] = 0x19;
        buf[1] = 0x01;
        System.arraycopy(domainHash, 0, buf, 2, 32);
        System.arraycopy(structHash, 0, buf, 34, 32);
        return Hash.sha3(buf);
    }

    /** ERC-7739: the owner signs a TypedDataSign digest wrapping the order under the exchange domain. */
    private static byte[] depositWalletDigest(byte[] exchangeDomainHash, byte[] orderStructHash,
            String walletAddress) {
        byte[] tuple = new byte[32 * 7];
        int offset = 0;
        System.arraycopy(SOLADY_TYPE_HASH, 0, tuple, offset, 32);
        offset += 32;
        System.arraycopy(orderStructHash, 0, tuple, offset, 32);
        offset += 32;
        System.arraycopy(DEPOSIT_WALLET_NAME_HASH, 0, tuple, offset, 32);
        offset += 32;
        System.arraycopy(DEPOSIT_WALLET_VERSION_HASH, 0, tuple, offset, 32);
        offset += 32;
        copyPadded(BigInteger.valueOf(CHAIN_ID), tuple, offset);
        offset += 32;
        copyAddress(walletAddress, tuple, offset);
        offset += 32;
        // trailing salt field is bytes32(0); buf is already zero-filled there
        byte[] typedDataSignStructHash = Hash.sha3(tuple);
        return eip712Digest(exchangeDomainHash, typedDataSignStructHash);
    }

    private static void copyPadded(BigInteger value, byte[] buf, int offset) {
        System.arraycopy(Numeric.toBytesPadded(value, 32), 0, buf, offset, 32);
    }

    private static void copyAddress(String hexAddress, byte[] buf, int offset) {
        byte[] addr = Numeric.hexStringToByteArray(hexAddress);
        System.arraycopy(addr, 0, buf, offset + (32 - addr.length), addr.length);
    }

    private static void copyBytes32(String hex, byte[] buf, int offset) {
        byte[] bytes = Numeric.hexStringToByteArray(hex);
        if (bytes.length != 32) {
            throw new IllegalArgumentException("expected 32 bytes, got " + bytes.length);
        }
        System.arraycopy(bytes, 0, buf, offset, 32);
    }
}
