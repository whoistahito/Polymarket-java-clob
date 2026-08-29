package com.polymarket.authentication;

import java.io.IOException;
import java.util.List;
import lombok.NonNull;

/**
 * Deliberate API-key lifecycle operations. Each call fails before sending when the
 * authority it needs is absent.
 */
public final class Authentication {

    private final SigningAuthority authority;
    private final ApiKeyDirectory directory;

    public Authentication(@NonNull SigningAuthority authority, @NonNull ApiKeyDirectory directory) {
        this.authority = authority;
        this.directory = directory;
    }

    public ApiCredentials createApiKey() throws IOException {
        return directory.create(authority.requireAccountSignerKey("createApiKey"));
    }

    public ApiCredentials deriveApiKey() throws IOException {
        return directory.derive(authority.requireAccountSignerKey("deriveApiKey"));
    }

    public List<String> apiKeys() throws IOException {
        return directory.list(authority.requireAccountSignerKey("apiKeys"));
    }

    public ApiKeyValidation validate() throws IOException {
        return directory.validate(
                authority.requireApiCredentials("validate"), signingAddress("validate"));
    }

    public ApiKeyDeletion deleteApiKey() throws IOException {
        return directory.delete(
                authority.requireApiCredentials("deleteApiKey"), signingAddress("deleteApiKey"));
    }

    private String signingAddress(String operation) {
        return authority.requireAccountSigner(operation);
    }
}
