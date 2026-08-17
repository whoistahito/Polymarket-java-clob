package com.polymarket.rfq;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.SigningIdentity;
import com.polymarket.builders.BuilderCredentials;
import java.io.IOException;

/** Port for the Builder Gateway requester flow: create a quote request, read its status. */
public interface RfqDirectory {

    /** Executes exactly once: a requester write is never transparently replayed. */
    RfqOutcome request(RfqRequest request, SigningIdentity identity,
            ApiCredentials accountCredentials, BuilderCredentials builderCredentials) throws IOException;

    RfqOutcome status(String rfqId, ApiCredentials accountCredentials, String address)
            throws IOException;
}
