// Generates signing vectors from the OFFICIAL Polymarket typed data using ethers v6 as an
// independent reference implementation. Nothing here reads the Java SDK.
const { ethers } = require("ethers");

const KEY = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
const wallet = new ethers.Wallet(KEY);

const ORDER_TYPE = [
  { name: "salt", type: "uint256" },
  { name: "maker", type: "address" },
  { name: "signer", type: "address" },
  { name: "tokenId", type: "uint256" },
  { name: "makerAmount", type: "uint256" },
  { name: "takerAmount", type: "uint256" },
  { name: "side", type: "uint8" },
  { name: "signatureType", type: "uint8" },
  { name: "timestamp", type: "uint256" },
  { name: "metadata", type: "bytes32" },
  { name: "builder", type: "bytes32" },
];

const TYPED_DATA_SIGN_TYPE = [
  { name: "contents", type: "Order" },
  { name: "name", type: "string" },
  { name: "version", type: "string" },
  { name: "chainId", type: "uint256" },
  { name: "verifyingContract", type: "address" },
  { name: "salt", type: "bytes32" },
];

const ZERO32 = "0x" + "00".repeat(32);
const DEPOSIT_WALLET = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8";

const EXCHANGE_V2 = "0xE111180000d2663C0091e4f400237545B87B996B";
const NEG_RISK_EXCHANGE_V2 = "0xe2222d279d744050d28e00520010520000310F59";
const EXCHANGE_V3 = "0xe3333700cA9d93003F00f0F71f8515005F6c00Aa";

const domain = (version, verifyingContract) => ({
  name: "Polymarket CTF Exchange",
  version,
  chainId: 137,
  verifyingContract,
});

// V2 timestamp is unix MILLISECONDS; V3 timestamp is unix SECONDS (per official docs).
const v2Order = (signatureType, maker, signer) => ({
  salt: "479249096354",
  maker,
  signer,
  tokenId: "71321045679252212594626385532706912750332728571942532289631379312455583992563",
  makerAmount: "5200000",
  takerAmount: "10000000",
  side: 0,
  signatureType,
  timestamp: "1773890758000",
  metadata: ZERO32,
  builder: ZERO32,
});

const v3Order = (signatureType, maker, signer) => ({
  salt: "479249096354",
  maker,
  signer,
  tokenId: "36196417523497374373069901922264126122921002471398991055768102573969893525569",
  makerAmount: "450000",
  takerAmount: "1000000",
  side: 0,
  signatureType,
  timestamp: "1773890758",
  metadata: ZERO32,
  builder: ZERO32,
});

const depositWrapper = (contents) => ({
  contents,
  name: "DepositWallet",
  version: "1",
  chainId: 137,
  verifyingContract: DEPOSIT_WALLET,
  salt: ZERO32,
});

const ORDER_TYPE_STRING = ethers.TypedDataEncoder.from({ Order: ORDER_TYPE }).encodeType("Order");

// The exact ERC-7739 envelope published as wrapDepositWalletSignature() on
// docs.polymarket.com/trading/place-orders and /trading/combos/market-makers:
// innerSignature || appDomainSeparator || contentsHash || contentsDescr || uint16(len).
function wrapDepositWalletSignature(innerSignature, dom, contents) {
  return ethers.concat([
    innerSignature,
    ethers.TypedDataEncoder.hashDomain(dom),
    ethers.TypedDataEncoder.hashStruct("Order", { Order: ORDER_TYPE }, contents),
    ethers.toUtf8Bytes(ORDER_TYPE_STRING),
    ethers.toBeHex(ORDER_TYPE_STRING.length, 2),
  ]);
}

async function vector(id, note, dom, types, primaryType, message) {
  const enc = ethers.TypedDataEncoder.from(types);
  const signature = await wallet.signTypedData(dom, types, message);
  const vec = {
    id,
    note,
    domain: dom,
    primaryType,
    types,
    message,
    encodeType: enc.encodeType(primaryType),
    typeHash: ethers.keccak256(ethers.toUtf8Bytes(enc.encodeType(primaryType))),
    domainSeparator: ethers.TypedDataEncoder.hashDomain(dom),
    structHash: ethers.TypedDataEncoder.hashStruct(primaryType, types, message),
    digest: ethers.TypedDataEncoder.hash(dom, types, message),
    signerAddress: wallet.address,
    signature,
  };
  if (primaryType === "TypedDataSign") {
    vec.contentsDescr = ORDER_TYPE_STRING;
    vec.contentsHash = ethers.TypedDataEncoder.hashStruct(
      "Order", { Order: ORDER_TYPE }, message.contents);
    // What the exchange's ERC-1271 check verifies. `signature` above is only its first 65 bytes.
    vec.wrappedSignature = wrapDepositWalletSignature(signature, dom, message.contents);
  }
  return vec;
}

(async () => {
  const eoa = wallet.address;
  const v2Dom = domain("2", EXCHANGE_V2);
  const v2NegRiskDom = domain("2", NEG_RISK_EXCHANGE_V2);
  const v3Dom = domain("3", EXCHANGE_V3);

  const vectors = [
    await vector("v2-eoa", "Exchange V2 token order, EOA (signatureType 0)",
      v2Dom, { Order: ORDER_TYPE }, "Order", v2Order(0, eoa, eoa)),
    await vector("v2-proxy", "Exchange V2 token order, Proxy Wallet (signatureType 1)",
      v2Dom, { Order: ORDER_TYPE }, "Order", v2Order(1, DEPOSIT_WALLET, eoa)),
    await vector("v2-safe", "Exchange V2 token order, Safe Wallet (signatureType 2)",
      v2Dom, { Order: ORDER_TYPE }, "Order", v2Order(2, DEPOSIT_WALLET, eoa)),
    await vector("v2-neg-risk-eoa", "Exchange V2 token order on the Neg Risk exchange, EOA",
      v2NegRiskDom, { Order: ORDER_TYPE }, "Order", v2Order(0, eoa, eoa)),
    await vector("v2-deposit-wallet",
      "Exchange V2 token order, Deposit Wallet (signatureType 3) wrapped for ERC-7739",
      v2Dom, { Order: ORDER_TYPE, TypedDataSign: TYPED_DATA_SIGN_TYPE }, "TypedDataSign",
      depositWrapper(v2Order(3, DEPOSIT_WALLET, eoa))),
    await vector("v3-eoa", "Exchange V3 Combo position order, EOA (signatureType 0)",
      v3Dom, { Order: ORDER_TYPE }, "Order", v3Order(0, eoa, eoa)),
    await vector("v3-proxy", "Exchange V3 Combo position order, Proxy Wallet (signatureType 1)",
      v3Dom, { Order: ORDER_TYPE }, "Order", v3Order(1, DEPOSIT_WALLET, eoa)),
    await vector("v3-safe", "Exchange V3 Combo position order, Safe Wallet (signatureType 2)",
      v3Dom, { Order: ORDER_TYPE }, "Order", v3Order(2, DEPOSIT_WALLET, eoa)),
    await vector("v3-deposit-wallet",
      "Exchange V3 Combo position order, Deposit Wallet (signatureType 3) wrapped for ERC-7739",
      v3Dom, { Order: ORDER_TYPE, TypedDataSign: TYPED_DATA_SIGN_TYPE }, "TypedDataSign",
      depositWrapper(v3Order(3, DEPOSIT_WALLET, eoa))),
  ];

  console.log(JSON.stringify({
    generatedBy: "ethers v" + ethers.version + " (independent reference implementation)",
    reviewedOn: "2026-08-23",
    privateKey: KEY,
    sources: [
      "https://docs.polymarket.com/trading/place-orders (Exchange V2 typed data)",
      "https://docs.polymarket.com/trading/combos/market-makers (Exchange V3 typed data)",
      "https://docs.polymarket.com/resources/contracts (contract addresses)",
    ],
    vectors,
  }, null, 2));
})();
