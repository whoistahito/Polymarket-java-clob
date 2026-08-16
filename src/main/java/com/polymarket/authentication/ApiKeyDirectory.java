package com.polymarket.authentication;

import java.io.IOException;
import java.util.List;

/**
 * Port for the API-key lifecycle. The domain declares it; an internal adapter implements
 * it, so no transport type reaches this package.
 */
public interface ApiKeyDirectory {

    ApiCredentials create(PrivateKeySigner signer) throws IOException;

    ApiCredentials derive(PrivateKeySigner signer) throws IOException;

    List<String> list(PrivateKeySigner signer) throws IOException;

    ApiKeyValidation validate(ApiCredentials credentials, String signingAddress) throws IOException;

    ApiKeyDeletion delete(ApiCredentials credentials, String signingAddress) throws IOException;
}
