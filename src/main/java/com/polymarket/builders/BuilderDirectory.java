package com.polymarket.builders;

import com.polymarket.authentication.ApiCredentials;
import java.io.IOException;
import java.util.List;

/**
 * Port for the builder credential lifecycle and builder-attributed trade reads. The domain
 * declares it; an internal adapter implements it, so no transport type reaches this package.
 */
public interface BuilderDirectory {

    BuilderCredentials create(ApiCredentials credentials, String address) throws IOException;

    List<BuilderCredentialSummary> list(ApiCredentials credentials, String address) throws IOException;

    BuilderCredentialRevocation revoke(ApiCredentials credentials, String address) throws IOException;

    BuilderTradePage trades(ApiCredentials credentials, String address, BuilderTradeQuery query,
            BuilderCursor cursor) throws IOException;
}
