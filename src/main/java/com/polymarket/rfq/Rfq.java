package com.polymarket.rfq;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.SigningIdentity;
import com.polymarket.builders.BuilderCredentials;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
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

/** The Combo requester flow: discover legs, request a Quote, accept it, then follow settlement. */
public final class Rfq {

    private final RfqDirectory directory;
    private final ComboMarketCatalog catalog;
    private final Clock clock;
    private final Sleeper sleeper;

    /** Injected so {@link #awaitSettlement} can be tested without a real wait. */
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

    /** The Quote arrives inline on this response; there is no quote to poll for afterwards. */
    public RfqOutcome request(@NonNull RfqRequest request, @NonNull SigningIdentity identity,
            @NonNull ApiCredentials accountCredentials,
            @NonNull BuilderCredentials builderCredentials) throws IOException {
        return directory.request(request, identity, accountCredentials, builderCredentials);
    }

    /**
     * Officially valid only after acceptance: before that the gateway answers HTTP 409, which
     * surfaces as {@link RfqOutcome.NotYetAccepted}.
     */
    public RfqOutcome status(@NonNull String rfqId, @NonNull ApiCredentials accountCredentials,
            @NonNull String accountSigner) throws IOException {
        return directory.status(rfqId, accountCredentials, accountSigner);
    }

    /**
     * Polls status after acceptance until settlement resolves or {@code timeout} elapses. A
     * local timeout is {@link RfqOutcome.Pending} — never a reported failure.
     */
    public RfqOutcome awaitSettlement(@NonNull String rfqId,
            @NonNull ApiCredentials accountCredentials, @NonNull String accountSigner,
            @NonNull Duration timeout, @NonNull Duration pollInterval) throws IOException {
        Instant deadline = clock.instant().plus(timeout);

        while (true) {
            RfqOutcome outcome = status(rfqId, accountCredentials, accountSigner);
            if (!(outcome instanceof RfqOutcome.Waiting)) {
                return outcome;
            }
            Instant now = clock.instant();
            if (!now.isBefore(deadline)) {
                return new RfqOutcome.Pending(rfqId);
            }
            // The wait is the caller's deadline, not the poll interval: sleeping a whole interval
            // past it would read once more and report Pending later than they asked.
            Duration remaining = Duration.between(now, deadline);
            try {
                sleeper.sleep(pollInterval.compareTo(remaining) < 0 ? pollInterval : remaining);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while following an RFQ to settlement", e);
            }
            if (!clock.instant().isBefore(deadline)) {
                return new RfqOutcome.Pending(rfqId);
            }
        }
    }

    /**
     * Signs the Quote's Combo position through the V3 path and accepts it. Direction, amounts,
     * Combo position and deadline all come from the Quote, so no caller can contradict it.
     */
    public RfqOutcome accept(@NonNull RfqOutcome.Quoted quote, @NonNull ComboQuoteSigner signer,
            @NonNull SigningContext context, @NonNull ApiCredentials accountCredentials,
            @NonNull BuilderCredentials builderCredentials) {
        if (!clock.instant().isBefore(quote.expiresAt())) {
            throw new IllegalArgumentException(
                    "quote " + quote.quoteId() + " expired at " + quote.expiresAt());
        }
        Side direction = quote.direction();
        PusdAmount pusdLeg;
        ShareQuantity shareLeg;
        // makerAmount is always maker_amount_e6 and takerAmount always taker_amount_e6, so the
        // legs swap roles with the direction rather than the amounts changing.
        if (direction == Side.BUY) {
            pusdLeg = baseUnitsToPusd(quote.amounts().makerAmountBaseUnits());
            shareLeg = baseUnitsToShares(quote.amounts().takerAmountBaseUnits());
        } else {
            shareLeg = baseUnitsToShares(quote.amounts().makerAmountBaseUnits());
            pusdLeg = baseUnitsToPusd(quote.amounts().takerAmountBaseUnits());
        }
        // Official: "order.builder must equal the returned builder_code."
        SignedOrder signedOrder = signer.sign(quote.comboPositionId(), direction, pusdLeg, shareLeg,
                context.withBuilder(quote.builderCode()));
        return directory.accept(quote.rfqId(), quote.quoteId(), signedOrder, context.identity(),
                accountCredentials, builderCredentials);
    }

    private static PusdAmount baseUnitsToPusd(long baseUnits) {
        return PusdAmount.of(BigDecimal.valueOf(baseUnits).movePointLeft(6));
    }

    private static ShareQuantity baseUnitsToShares(long baseUnits) {
        return ShareQuantity.of(BigDecimal.valueOf(baseUnits).movePointLeft(6));
    }
}
