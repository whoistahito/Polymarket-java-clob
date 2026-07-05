package com.polymarket.client;

import com.polymarket.model.*;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Sign;
import org.web3j.crypto.StructuredDataEncoder;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Order builder for creating and signing Polymarket orders.
 *
 * <p>Handles amount calculations, price rounding, EIP-712 signing, and payload construction.
 */
public final class OrderBuilder {

    private static final int TOKEN_DECIMALS = 6;
    private static final BigDecimal DECIMAL_MULTIPLIER = BigDecimal.TEN.pow(
        TOKEN_DECIMALS
    );
  private static final BigInteger MARKET_BUY_MAKER_STEP =
      BigInteger.TEN.pow(TOKEN_DECIMALS - 2); // 2dp
  private static final BigInteger MARKET_BUY_TAKER_STEP =
      BigInteger.TEN.pow(TOKEN_DECIMALS - 4); // 4dp
    private static final String ZERO_ADDRESS =
        "0x0000000000000000000000000000000000000000";

    // V2 exchange + neg-risk-V2 are identical across chains 137 and 80002.
    private static final String EXCHANGE_V2 =
        "0xE111180000d2663C0091e4f400237545B87B996B";
    private static final String NEG_RISK_EXCHANGE_V2 =
        "0xe2222d279d744050d28e00520010520000310F59";

    private static final Map<Integer, ContractConfig> CONTRACTS = Map.of(
        137,
        new ContractConfig(
            "0x4bFb41d5B3570DeFd03C39a9A4D8dE6Bd8B8982E",
            "0xC5d563A36AE78145C45a50134d48A1215220f80a",
            EXCHANGE_V2,
            NEG_RISK_EXCHANGE_V2
        ),
        80002,
        new ContractConfig(
            "0xdFE02Eb6733538f8Ea35D585af8DE5958AD99E40",
            "0xC5d563A36AE78145C45a50134d48A1215220f80a",
            EXCHANGE_V2,
            NEG_RISK_EXCHANGE_V2
        )
    );

    private static final Map<String, RoundConfig> ROUND_CONFIGS = Map.of(
        "0.1",
        new RoundConfig(1, 2, 3),
        "0.01",
        new RoundConfig(2, 2, 4),
        "0.001",
        new RoundConfig(3, 2, 5),
        "0.0001",
        new RoundConfig(4, 2, 6)
    );

    private final Credentials credentials;
    private final int chainId;
    private final SignatureType signatureType;
    private final String funderAddress;
    private final SecureRandom random = new SecureRandom();
    // ponytail: lazy — OrderUtils validates the chain ID in its constructor; OrderBuilder instead
    // throws at order-build time (CONTRACTS.get) so unsupported-chain construction is permitted.
    private com.polymarket.util.OrderUtils orderUtils;

    /**
     * Resolved CLOB order-protocol version (PMK-004). {@code 0} = unresolved; the first order build
     * against a {@code PolymarketClient} calls {@code resolveVersion()} and stores the result here.
     * Defaults to {@code 2} when used standalone (V2 is the production default).
     */
    private int version = 2;

    public OrderBuilder(Credentials credentials, int chainId) {
        this(credentials, chainId, SignatureType.EOA, null);
    }

    public OrderBuilder(
        Credentials credentials,
        int chainId,
        int signatureTypeValue,
        String funderAddress
    ) {
        this(credentials, chainId, SignatureType.values()[signatureTypeValue], funderAddress);
    }

    public OrderBuilder(
        Credentials credentials,
        int chainId,
        SignatureType signatureType,
        String funderAddress
    ) {
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.chainId = chainId;
        this.signatureType = signatureType;
        this.funderAddress = funderAddress;
    }

    private com.polymarket.util.OrderUtils orderUtils() {
        if (orderUtils == null) {
            orderUtils =
                new com.polymarket.util.OrderUtils(credentials, chainId, signatureType, funderAddress);
        }
        return orderUtils;
    }

    /** Sets the resolved CLOB order-protocol version (PMK-004). 1 = V1, 2 = V2. */
    public void setVersion(int version) {
        if (version != 1 && version != 2) {
            throw new IllegalArgumentException("Unsupported protocol version: " + version);
        }
        this.version = version;
    }

    /** Returns the resolved protocol version (default 2 when never set). */
    public int getVersion() {
        return version;
    }

    /** Signature type value for EOA (externally owned account). */
    public static final int SIGNATURE_TYPE_EOA = 0;
    /** Signature type value for Polymarket Proxy wallet. */
    public static final int SIGNATURE_TYPE_POLY_PROXY = 1;
    /** Signature type value for Polymarket Gnosis Safe wallet. */
    public static final int SIGNATURE_TYPE_POLY_GNOSIS_SAFE = 2;

    public String getMakerAddress() {
        return funderAddress != null ? funderAddress : credentials.getAddress();
    }

    public String getSignerAddress() {
        return credentials.getAddress();
    }

    /** Returns the numeric signature type value (0=EOA, 1=POLY_PROXY, 2=POLY_GNOSIS_SAFE). */
    public int getSignatureTypeValue() {
        return signatureType.getValue();
    }

    /**
     * Build and sign a limit order (UserOrder).
     *
     * @param userOrder The user order details
     * @param options   Creation options (tick size, neg risk)
     * @return A SignedOrder object ready to be posted
     */
    public SignedOrder buildOrder(
        UserOrder userOrder,
        CreateOrderOptions options
    ) {
    return buildOrder(userOrder, options, null);
  }

  /** Build and sign a limit order with optional order-type-aware amount normalization. */
  public SignedOrder buildOrder(
      UserOrder userOrder, CreateOrderOptions options, OrderType orderType) {
        validateInputs(userOrder, options);

        String tickSize = options.tickSize();
        BigDecimal tickSizeDecimal = new BigDecimal(tickSize);
        BigDecimal roundedPrice = roundToTickSize(
            userOrder.price(),
            tickSizeDecimal
        );

        RoundConfig roundConfig = ROUND_CONFIGS.getOrDefault(
            tickSize,
            ROUND_CONFIGS.get("0.01")
        );

        RawAmounts amounts = calculateAmounts(
            userOrder.side(),
            userOrder.size(),
            roundedPrice,
            roundConfig
        );

    if (isMarketStyleBuy(userOrder.side(), orderType)) {
      // Exchange requires market-buy style amounts for taker-like order types.
      amounts = normalizeMarketBuyPrecision(amounts);
    }

        String taker =
            userOrder.taker() != null ? userOrder.taker() : ZERO_ADDRESS;
        int feeRate =
            userOrder.feeRateBps() != null ? userOrder.feeRateBps() : 0;
        long expiration =
            userOrder.expiration() != null ? userOrder.expiration() : 0;
        long nonce = userOrder.nonce() != null ? userOrder.nonce() : 0;

        return createSignedOrder(
            userOrder.tokenID(),
            userOrder.side(),
            amounts,
            feeRate,
            expiration,
            nonce,
            taker,
            options.negRisk(),
            userOrder.metadata(),
            userOrder.builderCode()
        );
    }

  private boolean isMarketStyleBuy(Side side, OrderType orderType) {
    return side == Side.BUY && (orderType == OrderType.FOK || orderType == OrderType.FAK);
  }

  private RawAmounts normalizeMarketBuyPrecision(RawAmounts amounts) {
    return new RawAmounts(
        quantizeUnitsDown(amounts.makerAmount(), MARKET_BUY_MAKER_STEP),
        quantizeUnitsDown(amounts.takerAmount(), MARKET_BUY_TAKER_STEP));
  }

  private String quantizeUnitsDown(String rawUnits, BigInteger step) {
    BigInteger value = new BigInteger(rawUnits);
    return value.divide(step).multiply(step).toString();
  }

    /**
     * Build and sign a market order (UserMarketOrder).
     *
     * @param userMarketOrder The market order details
     * @param options         Creation options (tick size, neg risk)
     * @return A SignedOrder object ready to be posted
     */
    public SignedOrder buildMarketOrder(
        UserMarketOrder userMarketOrder,
        CreateOrderOptions options
    ) {
        validateMarketInputs(userMarketOrder, options);

        String tickSize = options.tickSize();
        BigDecimal tickSizeDecimal = new BigDecimal(tickSize);

        // For market orders, if price is missing, it should have been estimated by the client.
        // If provided, we validate/round it.
        BigDecimal price =
            userMarketOrder.price() != null
                ? userMarketOrder.price()
                : BigDecimal.ONE;
        BigDecimal roundedPrice = roundToTickSize(price, tickSizeDecimal);

        RoundConfig roundConfig = ROUND_CONFIGS.getOrDefault(
            tickSize,
            ROUND_CONFIGS.get("0.01")
        );

        RawAmounts amounts = calculateMarketAmounts(
            userMarketOrder.side(),
            userMarketOrder.amount(),
            roundedPrice,
            roundConfig
        );

        String taker =
            userMarketOrder.taker() != null
                ? userMarketOrder.taker()
                : ZERO_ADDRESS;
        int feeRate =
            userMarketOrder.feeRateBps() != null
                ? userMarketOrder.feeRateBps()
                : 0;
        long nonce =
            userMarketOrder.nonce() != null ? userMarketOrder.nonce() : 0;

        // Market orders have 0 expiration (FOK/FAK handled by order type in payload)
        return createSignedOrder(
            userMarketOrder.tokenID(),
            userMarketOrder.side(),
            amounts,
            feeRate,
            0,
            nonce,
            taker,
            options.negRisk(),
            null,
            null
        );
    }

    /**
     * Helper to create a PostOrderPayload from a SignedOrder.
     */
    public PostOrderPayload buildPayload(
        SignedOrder signedOrder,
        String ownerApiKey,
        OrderType orderType,
        boolean deferExec,
        boolean postOnly
    ) {
        // GTC and GTD support postOnly, others do not.
        Boolean po = (orderType == OrderType.GTC || orderType == OrderType.GTD)
            ? postOnly
            : null;

        return PostOrderPayload.builder()
            .order(signedOrder)
            .owner(ownerApiKey)
            .orderType(orderType)
            .deferExec(deferExec)
            .postOnly(po)
            .build();
    }

    /**
     * Convenience method: validate inputs, build, sign and package a limit order
     * into a raw {@code Map<String, Object>} representation.
     *
     * <p>This is the higher-level API expected by existing tests. Parameters
     * are plain strings to avoid coupling callers to model enums.
     *
     * @param tokenId   Token ID
     * @param side      "BUY" or "SELL"
     * @param price     Order price
     * @param size      Order size (must be positive)
     * @param tickSize  Market tick size (e.g. "0.01")
     * @param negRisk   Whether this is a neg-risk market
     * @param orderType Order type string (e.g. "GTC", "FOK")
     * @param apiKey    Owner API key (must not be null)
     * @return Map containing {@code "order"} (signed order map), {@code "owner"},
     *         {@code "orderType"}, {@code "deferExec"}
     */
    public Map<String, Object> createOrder(
        String tokenId,
        String side,
        BigDecimal price,
        BigDecimal size,
        String tickSize,
        boolean negRisk,
        String orderType,
        String apiKey
    ) {
        if (tokenId == null || tokenId.isEmpty()) {
            throw new IllegalArgumentException("tokenId is required");
        }
        if (side == null || (!side.equals("BUY") && !side.equals("SELL"))) {
            throw new IllegalArgumentException("side must be BUY or SELL");
        }
        if (apiKey == null) {
            throw new IllegalArgumentException("apiKey is required");
        }
        if (size == null || size.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }

        Side sideEnum = Side.valueOf(side);
        OrderType orderTypeEnum = OrderType.valueOf(orderType);

        UserOrder userOrder = UserOrder.builder()
            .tokenID(tokenId)
            .side(sideEnum)
            .price(price)
            .size(size)
            .feeRateBps(0)
            .build();

        CreateOrderOptions options = CreateOrderOptions.builder()
            .tickSize(tickSize)
            .negRisk(negRisk)
            .build();

    SignedOrder signedOrder = buildOrder(userOrder, options, orderTypeEnum);
        PostOrderPayload payload = buildPayload(signedOrder, apiKey, orderTypeEnum, false, false);

        // Convert to Map<String, Object> for backward-compatible return type
        Map<String, Object> orderMap = new LinkedHashMap<>();
        orderMap.put("salt", signedOrder.salt());
        orderMap.put("maker", signedOrder.maker());
        orderMap.put("signer", signedOrder.signer());
        orderMap.put("taker", signedOrder.taker());
        orderMap.put("tokenId", signedOrder.tokenId());
        orderMap.put("makerAmount", signedOrder.makerAmount());
        orderMap.put("takerAmount", signedOrder.takerAmount());
        orderMap.put("expiration", signedOrder.expiration());
        orderMap.put("nonce", signedOrder.nonce());
        orderMap.put("feeRateBps", signedOrder.feeRateBps());
        orderMap.put("side", signedOrder.side().name());
        orderMap.put("signatureType", signedOrder.signatureType().getValue());
        orderMap.put("signature", signedOrder.signature());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", orderMap);
        result.put("owner", apiKey);
        result.put("orderType", orderTypeEnum.name());
        result.put("deferExec", false);
        if (orderTypeEnum == OrderType.GTC || orderTypeEnum == OrderType.GTD) {
            result.put("postOnly", false);
        }
        return result;
    }

    /**
     * Convenience overload of {@link #createOrder} that accepts optional
     * feeRateBps, nonce/expiration and taker address.
     *
     * <p>For {@code GTD} orders {@code nonceOrExpiration} is used as the
     * expiration timestamp; for all other order types it is used as the nonce.
     */
    public Map<String, Object> createOrder(
        String tokenId,
        String side,
        BigDecimal price,
        BigDecimal size,
        String tickSize,
        boolean negRisk,
        String orderType,
        String apiKey,
        int feeRateBps,
        long nonceOrExpiration,
        String taker
    ) {
        if (tokenId == null || tokenId.isEmpty()) {
            throw new IllegalArgumentException("tokenId is required");
        }
        if (side == null || (!side.equals("BUY") && !side.equals("SELL"))) {
            throw new IllegalArgumentException("side must be BUY or SELL");
        }
        if (apiKey == null) {
            throw new IllegalArgumentException("apiKey is required");
        }
        if (size == null || size.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }

        Side sideEnum = Side.valueOf(side);
        OrderType orderTypeEnum = OrderType.valueOf(orderType);
        String resolvedTaker = taker != null ? taker : ZERO_ADDRESS;

        boolean isGtd = orderTypeEnum == OrderType.GTD;
        UserOrder userOrder = UserOrder.builder()
            .tokenID(tokenId)
            .side(sideEnum)
            .price(price)
            .size(size)
            .feeRateBps(feeRateBps)
            .nonce(isGtd ? 0 : (int) nonceOrExpiration)
            .expiration(isGtd ? nonceOrExpiration : 0L)
            .taker(resolvedTaker)
            .build();

        CreateOrderOptions options = CreateOrderOptions.builder()
            .tickSize(tickSize)
            .negRisk(negRisk)
            .build();

    SignedOrder signedOrder = buildOrder(userOrder, options, orderTypeEnum);
        PostOrderPayload payload = buildPayload(signedOrder, apiKey, orderTypeEnum, false, false);

        Map<String, Object> orderMap = new LinkedHashMap<>();
        orderMap.put("salt", signedOrder.salt());
        orderMap.put("maker", signedOrder.maker());
        orderMap.put("signer", signedOrder.signer());
        orderMap.put("taker", signedOrder.taker());
        orderMap.put("tokenId", signedOrder.tokenId());
        orderMap.put("makerAmount", signedOrder.makerAmount());
        orderMap.put("takerAmount", signedOrder.takerAmount());
        orderMap.put("expiration", signedOrder.expiration());
        orderMap.put("nonce", signedOrder.nonce());
        orderMap.put("feeRateBps", signedOrder.feeRateBps());
        orderMap.put("side", signedOrder.side().name());
        orderMap.put("signatureType", signedOrder.signatureType().getValue());
        orderMap.put("signature", signedOrder.signature());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", orderMap);
        result.put("owner", apiKey);
        result.put("orderType", orderTypeEnum.name());
        result.put("deferExec", false);
        if (orderTypeEnum == OrderType.GTC || orderTypeEnum == OrderType.GTD) {
            result.put("postOnly", false);
        }
        return result;
    }

    private void validateInputs(UserOrder order, CreateOrderOptions options) {
        if (order.tokenID() == null || order.tokenID().isEmpty()) {
            throw new IllegalArgumentException("tokenId is required");
        }
        if (
            order.size() == null || order.size().compareTo(BigDecimal.ZERO) <= 0
        ) {
            throw new IllegalArgumentException("size must be positive");
        }
        if (
                options.orderMinSize() != null
                        && order.size().compareTo(options.orderMinSize()) < 0
        ) {
            throw new IllegalArgumentException(
                    "size " + order.size() + " is below the market minimum order size "
                            + options.orderMinSize() + " shares"
            );
        }

        validatePrice(order.price(), options.tickSize());
    }

    private void validateMarketInputs(
        UserMarketOrder order,
        CreateOrderOptions options
    ) {
        if (order.tokenID() == null || order.tokenID().isEmpty()) {
            throw new IllegalArgumentException("tokenId is required");
        }
        if (
            order.amount() == null ||
            order.amount().compareTo(BigDecimal.ZERO) <= 0
        ) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (order.price() != null) {
            validatePrice(order.price(), options.tickSize());
        }
    }

    private void validatePrice(BigDecimal price, String tickSize) {
        BigDecimal tickSizeDecimal = new BigDecimal(tickSize);
        BigDecimal minPrice = tickSizeDecimal;
        BigDecimal maxPrice = BigDecimal.ONE.subtract(tickSizeDecimal);

        if (
            price == null ||
            price.compareTo(minPrice) < 0 ||
            price.compareTo(maxPrice) > 0
        ) {
            throw new IllegalArgumentException(
                "price must be between " +
                    minPrice +
                    " and " +
                    maxPrice +
                    " for tick size " +
                    tickSize
            );
        }
    }

    private RawAmounts calculateAmounts(
        Side side,
        BigDecimal size,
        BigDecimal price,
        RoundConfig config
    ) {
        // Standard limit order calculation
        BigDecimal roundedSize = size.setScale(
            config.size(),
            RoundingMode.DOWN
        );

        boolean isBuy = side == Side.BUY;
        BigDecimal baseAmount = roundedSize;
        BigDecimal calculatedAmount = roundedSize
            .multiply(price)
            .setScale(config.amount(), RoundingMode.HALF_UP);

        String makerAmount = toBlockchainUnits(
            isBuy ? calculatedAmount : baseAmount
        );
        String takerAmount = toBlockchainUnits(
            isBuy ? baseAmount : calculatedAmount
        );

        return new RawAmounts(makerAmount, takerAmount);
    }

    private RawAmounts calculateMarketAmounts(
        Side side,
        BigDecimal amount,
        BigDecimal price,
        RoundConfig config
    ) {
        // Market order calculation (from TS helpers.ts getMarketOrderRawAmounts)

        // Force price rounding down for market order logic safety
        BigDecimal rawPrice = price.setScale(config.price(), RoundingMode.DOWN);

        BigDecimal rawMakerAmt = amount.setScale(
            config.size(),
            RoundingMode.DOWN
        );
        BigDecimal rawTakerAmt;

        if (side == Side.BUY) {
            // BUY: amount is $$$ to buy. Maker (collateral) = amount. Taker (tokens) = amount / price
            rawTakerAmt = rawMakerAmt.divide(
                rawPrice,
                config.amount() + 4,
                RoundingMode.HALF_UP
            );
        } else {
            // SELL: amount is tokens to sell. Maker (tokens) = amount. Taker (collateral) = amount * price
            rawTakerAmt = rawMakerAmt.multiply(rawPrice);
        }

        // Adjust rounding for taker amount
        rawTakerAmt = adjustTakerAmountRounding(rawTakerAmt, config.amount());

        String makerAmountStr = toBlockchainUnits(rawMakerAmt);
        String takerAmountStr = toBlockchainUnits(rawTakerAmt);

        return new RawAmounts(makerAmountStr, takerAmountStr);
    }

    private BigDecimal adjustTakerAmountRounding(
        BigDecimal value,
        int decimals
    ) {
        if (value.scale() > decimals) {
            BigDecimal rounded = value.setScale(decimals + 4, RoundingMode.UP);
            if (rounded.scale() > decimals) {
                return rounded.setScale(decimals, RoundingMode.DOWN);
            }
            return rounded;
        }
        return value;
    }

    private SignedOrder createSignedOrder(
        String tokenId,
        Side side,
        RawAmounts amounts,
        int feeRateBps,
        long expiration,
        long nonce,
        String taker,
        Boolean negRisk
    ) {
        return createSignedOrder(tokenId, side, amounts, feeRateBps, expiration, nonce, taker, negRisk, null, null);
    }

    private SignedOrder createSignedOrder(
        String tokenId,
        Side side,
        RawAmounts amounts,
        int feeRateBps,
        long expiration,
        long nonce,
        String taker,
        Boolean negRisk,
        String metadata,
        String builderCode
    ) {
        ContractConfig contracts = CONTRACTS.get(chainId);
        if (contracts == null) {
            throw new IllegalArgumentException(
                "Unsupported chain ID: " + chainId
            );
        }

        boolean isNegRisk = negRisk != null && negRisk;

if (version == 2) {
            if (signatureType == SignatureType.POLY_1271
                && (funderAddress == null || funderAddress.isBlank())) {
                throw new IllegalArgumentException(
                    "A deposit wallet funder address is required with a POLY_1271 signature type");
            }
            // ponytail: delegate V2 struct + EIP-712 + POLY_1271 wrapping to OrderUtils (single
            // byte-level implementation) rather than re-implementing via web3j JSON. OrderBuilder
            // owns amount calc + salt/timestamp; OrderUtils owns the hash/sign.
            long salt = random.nextLong() & ((1L << 53) - 1);
            long timestamp = System.currentTimeMillis();
            String resolvedMetadata = (metadata == null || metadata.isBlank())
                ? OrderDataV2.BYTES32_ZERO : metadata;
            String resolvedBuilder = (builderCode == null || builderCode.isBlank())
                ? OrderDataV2.BYTES32_ZERO : builderCode;

            OrderDataV2 v2 = OrderDataV2.builder()
                .salt(java.math.BigInteger.valueOf(salt))
                .maker(getMakerAddress())
                .signer(getSignerAddress())
                .tokenId(tokenId)
                .makerAmount(new java.math.BigInteger(amounts.makerAmount()))
                .takerAmount(new java.math.BigInteger(amounts.takerAmount()))
                .side(side)
                .signatureType(signatureType)
                .timestamp(java.math.BigInteger.valueOf(timestamp))
                .metadata(resolvedMetadata)
                .builder(resolvedBuilder)
                .build();
            SignedOrder signed = orderUtils().buildSignedOrderV2(v2, isNegRisk);
            // Preserve the outer-payload expiration (V2 carries it outside the signed struct).
            return SignedOrder.v2Builder()
                .salt(signed.salt())
                .maker(signed.maker())
                .signer(signed.signer())
                .tokenId(signed.tokenId())
                .makerAmount(signed.makerAmount())
                .takerAmount(signed.takerAmount())
                .side(signed.side())
                .expiration(String.valueOf(expiration))
                .signatureType(signed.signatureType())
                .timestamp(signed.timestamp())
                .metadata(signed.metadata())
                .builderCode(signed.builderCode())
                .signature(signed.signature())
                .build();
        }

        // V1 path — byte-identical to the pre-V2 implementation.
        if (signatureType == SignatureType.POLY_1271) {
            throw new IllegalArgumentException(
                "signature type POLY_1271 is not supported for V1 orders"
            );
        }
        String exchangeAddress = isNegRisk
            ? contracts.negRiskExchange()
            : contracts.exchange();

        // Mask to IEEE 754 float64 safe integer range (<= 2^53-1) to match Rust SDK
        // rs-clob-client/src/clob/order_builder.rs: fn to_ieee_754_int(salt: u64) -> u64
        long salt = random.nextLong() & ((1L << 53) - 1);

        String signature;
        try {
            signature = signOrderParams(
                exchangeAddress,
                salt,
                getMakerAddress(),
                getSignerAddress(),
                taker,
                tokenId,
                amounts.makerAmount(),
                amounts.takerAmount(),
                expiration,
                nonce,
                feeRateBps,
                side,
                signatureType
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to sign order", e);
        }

        return SignedOrder.builder()
            .salt(salt)
            .maker(getMakerAddress())
            .signer(getSignerAddress())
            .taker(taker)
            .tokenId(tokenId)
            .makerAmount(amounts.makerAmount())
            .takerAmount(amounts.takerAmount())
            .expiration(String.valueOf(expiration))
            .nonce(String.valueOf(nonce))
            .feeRateBps(String.valueOf(feeRateBps))
            .version(1)
            .side(side)
            .signatureType(signatureType)
            .signature(signature)
            .build();
    }

    private String signOrderParams(
        String exchangeAddress,
        long salt,
        String maker,
        String signer,
        String taker,
        String tokenId,
        String makerAmount,
        String takerAmount,
        long expiration,
        long nonce,
        int feeRateBps,
        Side side,
        SignatureType sigType
    ) throws IOException {
        String json = buildOrderEip712Json(
            exchangeAddress,
            salt,
            maker,
            signer,
            taker,
            tokenId,
            makerAmount,
            takerAmount,
            expiration,
            nonce,
            feeRateBps,
            side,
            sigType
        );
        StructuredDataEncoder encoder = new StructuredDataEncoder(json);
        byte[] hash = encoder.hashStructuredData();

        Sign.SignatureData sig = Sign.signMessage(
            hash,
            credentials.getEcKeyPair(),
            false
        );
        return toHexString(sig);
    }

    /**
     * V2 EIP-712 signing is delegated to {@link com.polymarket.util.OrderUtils#buildSignedOrderV2};
     * this stub retained for future direct-sign callers.
     */
    private String buildOrderEip712Json(
        String exchangeAddress,
        long salt,
        String maker,
        String signer,
        String taker,
        String tokenId,
        String makerAmount,
        String takerAmount,
        long expiration,
        long nonce,
        int feeRateBps,
        Side side,
        SignatureType sigType
    ) {
        int sideInt = (side == Side.BUY) ? 0 : 1;
        int sigTypeInt = sigType.getValue();

        return """
        {
            "types": {
                "EIP712Domain": [
                    {"name": "name", "type": "string"},
                    {"name": "version", "type": "string"},
                    {"name": "chainId", "type": "uint256"},
                    {"name": "verifyingContract", "type": "address"}
                ],
                "Order": [
                    {"name": "salt", "type": "uint256"},
                    {"name": "maker", "type": "address"},
                    {"name": "signer", "type": "address"},
                    {"name": "taker", "type": "address"},
                    {"name": "tokenId", "type": "uint256"},
                    {"name": "makerAmount", "type": "uint256"},
                    {"name": "takerAmount", "type": "uint256"},
                    {"name": "expiration", "type": "uint256"},
                    {"name": "nonce", "type": "uint256"},
                    {"name": "feeRateBps", "type": "uint256"},
                    {"name": "side", "type": "uint8"},
                    {"name": "signatureType", "type": "uint8"}
                ]
            },
            "primaryType": "Order",
            "domain": {
                "name": "Polymarket CTF Exchange",
                "version": "1",
                "chainId": %d,
                "verifyingContract": "%s"
            },
            "message": {
                "salt": %s,
                "maker": "%s",
                "signer": "%s",
                "taker": "%s",
                "tokenId": "%s",
                "makerAmount": "%s",
                "takerAmount": "%s",
                "expiration": %s,
                "nonce": %s,
                "feeRateBps": %s,
                "side": %d,
                "signatureType": %d
            }
        }
        """.formatted(
                chainId,
                exchangeAddress,
                salt,
                maker,
                signer,
                taker,
                tokenId,
                makerAmount,
                takerAmount,
                expiration,
                nonce,
                feeRateBps,
                sideInt,
                sigTypeInt
            );
    }

    private String toBlockchainUnits(BigDecimal amount) {
        return amount
            .multiply(DECIMAL_MULTIPLIER)
            .setScale(0, RoundingMode.DOWN)
            .toBigInteger()
            .toString();
    }

    private BigDecimal roundToTickSize(BigDecimal price, BigDecimal tickSize) {
        return price
            .divide(tickSize, 0, RoundingMode.HALF_UP)
            .multiply(tickSize);
    }

    private String toHexString(Sign.SignatureData sig) {
        byte[] combined = new byte[65];
        System.arraycopy(sig.getR(), 0, combined, 0, 32);
        System.arraycopy(sig.getS(), 0, combined, 32, 32);
        combined[64] = sig.getV()[0];
        return Numeric.toHexString(combined);
    }

    private record RawAmounts(String makerAmount, String takerAmount) {}

    private record RoundConfig(int price, int size, int amount) {}

    private record ContractConfig(
        String exchange,
        String negRiskExchange,
        String exchangeV2,
        String negRiskExchangeV2
    ) {}

    /**
     * Resolves the EIP-712 verifying contract for the given chain, resolved protocol version,
     * and neg-risk flag (PMK-003).
     *
     * <p>Returns the exact checksummed address string (mixed case preserved).
     *
     * @param chainId the chain ID (137 = Polygon Mainnet, 80002 = Amoy Testnet)
     * @param version the resolved protocol version (1 or 2)
     * @param negRisk {@code true} to use the neg-risk exchange contract
     * @return the verifying contract address
     * @throws IllegalArgumentException if the chain ID or version is unsupported
     */
    public static String resolveVerifyingContract(int chainId, int version, boolean negRisk) {
        ContractConfig contracts = CONTRACTS.get(chainId);
        if (contracts == null) {
            throw new IllegalArgumentException("Unsupported chain ID: " + chainId);
        }
        return switch (version) {
            case 1 -> negRisk ? contracts.negRiskExchange() : contracts.exchange();
            case 2 -> negRisk ? contracts.negRiskExchangeV2() : contracts.exchangeV2();
            default -> throw new IllegalArgumentException(
                "Unsupported protocol version: " + version
            );
        };
    }
}
