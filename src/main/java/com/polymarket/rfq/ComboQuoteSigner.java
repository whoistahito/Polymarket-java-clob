package com.polymarket.rfq;

import com.polymarket.markets.PositionId;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
import com.polymarket.trading.Side;
import com.polymarket.trading.SignedOrder;
import com.polymarket.trading.SigningContext;
import lombok.NonNull;

/**
 * Signs the exact base-unit legs a Builder Gateway Quote returned. A Combo quote is priced by the
 * maker, not by a CLOB tick grid, so this seam takes legs rather than a price — and it takes a
 * {@link PositionId}, so a token order (which does have a grid) can never reach it.
 */
public interface ComboQuoteSigner {

    SignedOrder sign(@NonNull PositionId position, @NonNull Side side, @NonNull PusdAmount pusdLeg,
            @NonNull ShareQuantity shareLeg, @NonNull SigningContext context);
}
