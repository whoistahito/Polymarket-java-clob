package com.polymarket.authentication;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Deliberate API-key lifecycle operations. Each call fails before sending when the
 * authority it needs is absent.
 */
public final class Authentication {

    private final SigningAuthority authority;
    private final ApiKeyDirectory directory;

    public Authentication(SigningAuthority authority, ApiKeyDirectory directory) {
        this.authority = Objects.requireNonNull(authority, "authority");
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    public ApiCredentials createApiKey() throws IOException {
        return directory.create(authority.requireLocalSigner("createApiKey"));
    }

    public ApiCredentials deriveApiKey() throws IOException {
        return directory.derive(authority.requireLocalSigner("deriveApiKey"));
    }

    public List<String> apiKeys() throws IOException {
        return directory.list(authority.requireLocalSigner("apiKeys"));
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
        return authority.requireSigningAddress(operation);
    }
}
