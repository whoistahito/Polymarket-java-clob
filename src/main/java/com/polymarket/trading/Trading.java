package com.polymarket.trading;

import com.polymarket.markets.AssetId;
import com.polymarket.markets.MarketRules;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
import java.util.Objects;

/** Signing and submission are independently reachable; {@link #place} is a thin convenience over both. */
public final class Trading {

    private final OrderSigner signer;
    private final OrderSubmitter submitter;

    public Trading(OrderSigner signer, OrderSubmitter submitter) {
        this.signer = Objects.requireNonNull(signer, "signer");
        this.submitter = Objects.requireNonNull(submitter, "submitter");
    }

    public SignedOrder sign(AssetId asset, Side side, PusdAmount pusdLeg, ShareQuantity shareLeg,
            MarketRules rules, SigningContext context) {
        return signer.sign(asset, side, pusdLeg, shareLeg, rules, context);
    }

    /** Never replayed: one signed order produces exactly one {@code POST /order}. */
    public SubmissionOutcome submit(SignedOrder order, OrderPlacement placement) {
        return submitter.submit(order, placement);
    }

    public SubmissionOutcome place(AssetId asset, Side side, PusdAmount pusdLeg,
            ShareQuantity shareLeg, MarketRules rules, SigningContext context, OrderPlacement placement) {
        return submit(sign(asset, side, pusdLeg, shareLeg, rules, context), placement);
    }
}
