package com.polymarket.builders;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.SigningAuthority;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Builder credential lifecycle and builder-attributed trade reads. Every operation is
 * L2-authenticated and fails before sending when API credentials are absent.
 */
public final class Builders {

    private final SigningAuthority authority;
    private final BuilderDirectory directory;

    public Builders(SigningAuthority authority, BuilderDirectory directory) {
        this.authority = Objects.requireNonNull(authority, "authority");
        this.directory = Objects.requireNonNull(directory, "directory");
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

    public BuilderTradePage trades() throws IOException {
        return trades(BuilderTradeQuery.create(), BuilderCursor.first());
    }

    public BuilderTradePage trades(BuilderTradeQuery query) throws IOException {
        return trades(query, BuilderCursor.first());
    }

    public BuilderTradePage trades(BuilderCursor cursor) throws IOException {
        return trades(BuilderTradeQuery.create(), cursor);
    }

    public BuilderTradePage trades(BuilderTradeQuery query, BuilderCursor cursor) throws IOException {
        return directory.trades(credentials("trades"), address("trades"),
                Objects.requireNonNull(query, "query"), Objects.requireNonNull(cursor, "cursor"));
    }

    private ApiCredentials credentials(String operation) {
        return authority.requireApiCredentials(operation);
    }

    private String address(String operation) {
        return authority.requireAccountSigner(operation);
    }
}
