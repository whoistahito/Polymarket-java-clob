// Derives taker-fee examples from the OFFICIAL Polymarket fee formula using exact BigInt
// decimal arithmetic as an independent reference implementation. Nothing here reads the Java SDK.
// Official formula (https://docs.polymarket.com/trading/fees): fee = C x feeRate x p x (1 - p).

const dec = (text) => {
  const [whole, frac = ""] = text.split(".");
  return { n: BigInt(whole + frac), s: frac.length };
};

const mul = (a, b) => ({ n: a.n * b.n, s: a.s + b.s });

const oneMinus = (a) => ({ n: 10n ** BigInt(a.s) - a.n, s: a.s });

const format = (a) => {
  const negative = a.n < 0n;
  const digits = (negative ? -a.n : a.n).toString().padStart(a.s + 1, "0");
  const text = a.s === 0 ? digits : digits.slice(0, -a.s) + "." + digits.slice(-a.s);
  return (negative ? "-" : "") + text;
};

// Half-up rounding to `places`, on the exact scaled integer — no floating point anywhere.
const round = (a, places) => {
  if (a.s <= places) {
    return format({ n: a.n * 10n ** BigInt(places - a.s), s: places });
  }
  const divisor = 10n ** BigInt(a.s - places);
  const scaled = (a.n * 2n + divisor) / (divisor * 2n);
  return format({ n: scaled, s: places });
};

const fee = (shares, feeRate, price) => {
  const p = dec(price);
  return mul(mul(dec(shares), dec(feeRate)), mul(p, oneMinus(p)));
};

const example = (id, category, shares, feeRate, price, publishedUsdc, note) => {
  const exact = fee(shares, feeRate, price);
  const entry = {
    id,
    category,
    shares,
    feeRate,
    price,
    exactFeeUsdc: format(exact),
    feeAtFivePlaces: round(exact, 5),
  };
  if (publishedUsdc !== null) entry.publishedUsdc = publishedUsdc;
  if (note) entry.note = note;
  return entry;
};

// `publishedUsdc` values are copied verbatim from the fee tables on docs.polymarket.com/trading/fees.
const derivedExamples = [
  example("crypto-100-shares-0.01", "Crypto", "100", "0.07", "0.01", "0.07"),
  example("crypto-100-shares-0.10", "Crypto", "100", "0.07", "0.10", "0.63"),
  example("crypto-100-shares-0.30", "Crypto", "100", "0.07", "0.30", "1.47"),
  example("crypto-100-shares-0.50", "Crypto", "100", "0.07", "0.50", "1.75"),
  example("crypto-100-shares-0.70", "Crypto", "100", "0.07", "0.70", "1.47"),
  example("crypto-100-shares-0.99", "Crypto", "100", "0.07", "0.99", "0.07"),
  example("sports-100-shares-0.50", "Sports", "100", "0.05", "0.50", "1.25"),
  example("politics-100-shares-0.25", "Politics", "100", "0.04", "0.25", "0.75"),
  example("geopolitics-100-shares-0.50", "Geopolitics", "100", "0", "0.50", null,
    "Geopolitical and world events markets are fee-free."),
  example("crypto-100-shares-0.0025-tick", "Crypto", "100", "0.07", "0.0025", null,
    "A 0.0025 tick price needs the documented 5-decimal rounding; not in the published table."),
  example("crypto-0.1-shares-0.0001", "Crypto", "0.1", "0.07", "0.0001", null,
    "Below the 0.00001 USDC floor, so the charged fee rounds to zero."),
];

// Taker/maker/rebate columns copied verbatim from the fee table on docs.polymarket.com/trading/fees.
const rate = (takerRate, makerRebate) => ({ takerRate, makerRate: "0", makerRebate });

console.log(JSON.stringify({
  reviewedOn: "2026-08-23",
  sources: {
    formulaAndTables: "https://docs.polymarket.com/trading/fees.md",
    perMarketParameters: "https://docs.polymarket.com/market-data/market-details.md#trading-fees",
    clobBaseFee: "https://docs.polymarket.com/api-spec/clob-openapi.yaml (GET /fee-rate)",
    feesAtMatchTime: "https://docs.polymarket.com/changelog/predictions.md (Apr 17, 2026)",
  },
  formula: "fee = C x feeRate x p x (1 - p)",
  formulaAsPublished: "fee = C × feeRate × p × (1 - p)",
  variables: { C: "shares traded", p: "price of the shares" },
  takerOnly: true,
  makersNeverCharged: true,
  appliedAtMatchTime: true,
  carriedOnTheOrder: false,
  quotedIn: "USDC",
  symmetricAroundHalf: true,
  symmetryNote: "A trade at 30c incurs the same dollar fee as a trade at 70c.",
  precision: {
    decimalPlaces: 5,
    smallestChargeableFee: "0.00001",
    belowTheFloorRoundsToZero: true,
    roundingModeIsPublished: false,
    roundingNote:
      "The fee page states five-decimal rounding but not the tie-breaking mode. Every pinned "
      + "example rounds unambiguously, so no mode is asserted.",
  },
  categoryRates: {
    Crypto: rate("0.07", "0.20"),
    Sports: rate("0.05", "0.15"),
    Finance: rate("0.04", "0.25"),
    Politics: rate("0.04", "0.25"),
    Economics: rate("0.05", "0.25"),
    Culture: rate("0.05", "0.25"),
    Weather: rate("0.05", "0.25"),
    "Other / General": rate("0.05", "0.25"),
    Mentions: rate("0.04", "0.25"),
    Tech: rate("0.04", "0.25"),
    Geopolitics: rate("0", null),
  },
  perMarketSchedule: {
    source: "https://docs.polymarket.com/market-data/market-details.md#trading-fees",
    fields: ["feesEnabled", "feeSchedule.rate", "feeSchedule.exponent", "feeSchedule.takerOnly",
      "feeSchedule.rebateRate"],
    rateIsDecimalCoefficient: true,
    exampleRate: "0.04",
    exampleExponent: 1,
    note: "feeSchedule.rate is the feeRate of the formula, as a decimal coefficient, not basis points.",
  },
  unitContradiction: {
    clobFeeRateEndpoint: "GET /fee-rate returns base_fee as an integer in BASIS POINTS (example 30).",
    gammaFeeSchedule: "feeSchedule.rate is a DECIMAL COEFFICIENT (0.04 - 0.07, i.e. 400 - 700 bps).",
    contradiction:
      "The two official representations of the same coefficient disagree in unit and in magnitude. "
      + "Never feed base_fee into the formula as a coefficient, and never feed feeSchedule.rate in "
      + "as basis points.",
  },
  derivedExamples,
  derivation:
    "Derived by docs/protocol/gen-fee-vectors.js with exact BigInt decimal arithmetic (no floating "
    + "point, no Java SDK code). Each publishedUsdc is Polymarket's own table value and is the "
    + "independent check on the derivation.",
}, null, 2));
