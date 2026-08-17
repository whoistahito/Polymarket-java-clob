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
import java.util.Objects;

/** The Combo requester flow: create a quote request, then read or wait for its status. */
public final class Rfq {

    private final RfqDirectory directory;
    private final Clock clock;
    private final Sleeper sleeper;

    /** Injected so {@link #waitForQuote} can be tested without a real wait. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    public Rfq(RfqDirectory directory, Clock clock) {
        this(directory, clock, d -> Thread.sleep(d.toMillis()));
    }

    public Rfq(RfqDirectory directory, Clock clock, Sleeper sleeper) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
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
    public RfqOutcome waitForQuote(String rfqId, ApiCredentials accountCredentials, String address,
            Duration timeout, Duration pollInterval) throws IOException {
        Objects.requireNonNull(rfqId, "rfqId");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(pollInterval, "pollInterval");
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
    public RfqOutcome accept(RfqOutcome.Quoted quote, Side side, OrderSigner signer,
            SigningContext context, ApiCredentials accountCredentials, BuilderCredentials builderCredentials) {
        Objects.requireNonNull(quote, "quote");
        Objects.requireNonNull(side, "side");
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
