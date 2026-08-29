package com.polymarket.trading;

import com.polymarket.markets.AssetId;
import com.polymarket.markets.MarketRules;
import com.polymarket.markets.Price;
import com.polymarket.markets.ShareQuantity;
import lombok.NonNull;

/**
 * Signs a resolved CLOB order entirely offline. The asset's sealed type is the only routing
 * decision: a {@link com.polymarket.markets.TokenId} signs against Exchange V2, a
 * {@link com.polymarket.markets.PositionId} against Exchange V3 — no string heuristics.
 */
public interface OrderSigner {

    /**
     * The one signing seam, and it is priced: the Market Rule Snapshot is enforced — tradeable
     * bounds, tick grid and live minimum shares — and the pUSD leg derived at the grid's amount
     * precision. There is no leg-only form, so no caller can imply a price the snapshot never saw.
     */
    SignedOrder sign(@NonNull AssetId asset, @NonNull Side side, @NonNull Price price,
            @NonNull ShareQuantity shares, @NonNull MarketRules rules,
            @NonNull SigningContext context);
}
