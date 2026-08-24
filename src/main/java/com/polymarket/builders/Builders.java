package com.polymarket.builders;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.SigningAuthority;
import java.io.IOException;
import java.util.List;
import lombok.NonNull;

/**
 * Builder credential lifecycle and builder-attributed trade reads. Every operation is
 * L2-authenticated and fails before sending when API credentials are absent.
 */
public final class Builders {

    private final SigningAuthority authority;
    private final BuilderDirectory directory;

    public Builders(@NonNull SigningAuthority authority, @NonNull BuilderDirectory directory) {
        this.authority = authority;
        this.directory = directory;
    }

    public BuilderCredentials createCredentials() throws IOException {
        return directory.create(credentials("createCredentials"), address("createCredentials"));
    }

    public List<BuilderCredentialSummary> listCredentials() throws IOException {
        return directory.list(credentials("listCredentials"), address("listCredentials"));
    }

    public BuilderCredentialRevocation revokeCredentials() throws IOException {
        return directory.revoke(credentials("revokeCredentials"), address("revokeCredentials"));
    }

    /** There is no unfiltered form: the query carries the Builder code the CLOB requires. */
    public BuilderTradePage trades(@NonNull BuilderTradeQuery query) throws IOException {
        return trades(query, BuilderCursor.first());
    }

    public BuilderTradePage trades(@NonNull BuilderTradeQuery query, @NonNull BuilderCursor cursor)
            throws IOException {
        return directory.trades(credentials("trades"), address("trades"), query, cursor);
    }

    private ApiCredentials credentials(String operation) {
        return authority.requireApiCredentials(operation);
    }

    private String address(String operation) {
        return authority.requireAccountSigner(operation);
    }
}
