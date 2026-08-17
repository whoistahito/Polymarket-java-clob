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

    RfqOutcome status(String rfqId, ApiCredentials accountCredentials, String address)
            throws IOException;

    /**
     * Executes exactly once. Connection loss or a generic failure never throws — it returns
     * {@link RfqOutcome.Unknown} carrying {@code rfqId} so the caller can poll status instead
     * of re-sending the acceptance.
     */
    RfqOutcome accept(String rfqId, String quoteId, SignedOrder signedOrder,
            ApiCredentials accountCredentials, BuilderCredentials builderCredentials);
}
