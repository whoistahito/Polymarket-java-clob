package com.polymarket.rfq;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.SigningIdentity;
import com.polymarket.builders.BuilderCredentials;
import com.polymarket.markets.MarketRules;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
import com.polymarket.markets.TickSize;
import com.polymarket.trading.OrderSigner;
import com.polymarket.trading.Side;
import com.polymarket.trading.SignedOrder;
import com.polymarket.trading.SigningContext;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.NonNull;

/** The Combo requester flow: create a quote request, then read or wait for its status. */
public final class Rfq {

    private final RfqDirectory directory;
    private final ComboMarketCatalog catalog;
    private final Clock clock;
    private final Sleeper sleeper;

    /** Injected so {@link #waitForQuote} can be tested without a real wait. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    public Rfq(@NonNull RfqDirectory directory, @NonNull ComboMarketCatalog catalog,
            @NonNull Clock clock) {
        this(directory, catalog, clock, d -> Thread.sleep(d.toMillis()));
    }

    public Rfq(@NonNull RfqDirectory directory, @NonNull ComboMarketCatalog catalog,
            @NonNull Clock clock, @NonNull Sleeper sleeper) {
        this.directory = directory;
        this.catalog = catalog;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    /** Combo-eligible markets and their leg Position IDs, read from the official catalog. */
    public ComboMarketPage comboMarkets(@NonNull ComboMarketQuery query) throws IOException {
        return catalog.comboMarkets(query);
    }

    public RfqOutcome request(RfqRequest request, SigningIdentity identity,
            ApiCredentials accountCredentials, BuilderCredentials builderCredentials) throws IOException {
        return directory.request(request, identity, accountCredentials, builderCredentials);
    }

    public RfqOutcome status(String rfqId, ApiCredentials accountCredentials, String address)
            throws IOException {
        return directory.status(rfqId, accountCredentials, address);
    }

    /**
     * Polls status until a quote is ready or a terminal-without-fill state is reached, or
     * {@code timeout} elapses. A timeout is {@link RfqOutcome.Pending}, never a reported failure.
     */
    public RfqOutcome waitForQuote(@NonNull String rfqId, ApiCredentials accountCredentials,
            String address, @NonNull Duration timeout, @NonNull Duration pollInterval)
            throws IOException {
        Instant deadline = clock.instant().plus(timeout);

        while (true) {
            RfqOutcome outcome = status(rfqId, accountCredentials, address);
            if (!(outcome instanceof RfqOutcome.Waiting)) {
                return outcome;
            }
            if (!clock.instant().isBefore(deadline)) {
                return new RfqOutcome.Pending(rfqId);
            }
            try {
                sleeper.sleep(pollInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while waiting for an RFQ quote", e);
            }
        }
    }

    /** V3 has no neg-risk variant, so the rules passed to {@code signer} only ever supply a grid. */
    private static final MarketRules V3_RULES =
            new MarketRules(TickSize.of("0.01"), ShareQuantity.of("0.01"), false);

    /**
     * Signs the quote's combo position through the V3 path and accepts it. Rejects an expired
     * quote before sending — {@code side} must match the direction the original request used.
     */
    public RfqOutcome accept(@NonNull RfqOutcome.Quoted quote, @NonNull Side side,
            @NonNull OrderSigner signer, @NonNull SigningContext context,
            ApiCredentials accountCredentials, BuilderCredentials builderCredentials) {
        if (!clock.instant().isBefore(quote.expiresAt())) {
            throw new IllegalArgumentException(
                    "quote " + quote.quoteId() + " expired at " + quote.expiresAt());
        }
        PusdAmount pusdLeg;
        ShareQuantity shareLeg;
        if (side == Side.BUY) {
            pusdLeg = baseUnitsToPusd(quote.makerAmountBaseUnits());
            shareLeg = baseUnitsToShares(quote.takerAmountBaseUnits());
        } else {
            shareLeg = baseUnitsToShares(quote.makerAmountBaseUnits());
            pusdLeg = baseUnitsToPusd(quote.takerAmountBaseUnits());
        }
        // Official: "order.builder must equal the returned builder_code."
        SignedOrder signedOrder = signer.sign(quote.comboPositionId(), side, pusdLeg, shareLeg,
                V3_RULES, context.withBuilder(quote.builderCode()));
        return directory.accept(quote.rfqId(), quote.quoteId(), signedOrder,
                accountCredentials, builderCredentials);
    }

    private static PusdAmount baseUnitsToPusd(long baseUnits) {
        return PusdAmount.of(BigDecimal.valueOf(baseUnits).movePointLeft(6));
    }

    private static ShareQuantity baseUnitsToShares(long baseUnits) {
        return ShareQuantity.of(BigDecimal.valueOf(baseUnits).movePointLeft(6));
    }
}
