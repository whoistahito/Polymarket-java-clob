package com.polymarket.rfq;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.SigningIdentity;
import com.polymarket.builders.BuilderCredentials;
import java.io.IOException;
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
}
