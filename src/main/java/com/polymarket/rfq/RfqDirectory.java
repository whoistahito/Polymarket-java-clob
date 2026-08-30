package com.polymarket.rfq;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.SigningIdentity;
import com.polymarket.builders.BuilderCredentials;
import com.polymarket.trading.SignedOrder;
import java.io.IOException;

/** Port for the Builder Gateway requester flow: create a quote request, read or accept it. */
public interface RfqDirectory {

    /** Executes exactly once: a requester write is never transparently replayed. */
    RfqOutcome request(RfqRequest request, SigningIdentity identity,
            ApiCredentials accountCredentials, BuilderCredentials builderCredentials) throws IOException;

    /** Officially answerable only after acceptance; before that the gateway returns HTTP 409. */
    RfqOutcome status(String rfqId, ApiCredentials accountCredentials, String accountSigner)
            throws IOException;

    /**
     * Executes exactly once. {@code identity} authenticates the account — the Signed Order's own
     * addresses never decide who signs the headers. Connection loss or a generic failure never
     * throws: it returns {@link RfqOutcome.Unknown} carrying {@code rfqId} so the caller polls
     * status instead of re-sending the acceptance.
     */
    RfqOutcome accept(String rfqId, String quoteId, SignedOrder signedOrder,
            SigningIdentity identity, ApiCredentials accountCredentials,
            BuilderCredentials builderCredentials);
}
