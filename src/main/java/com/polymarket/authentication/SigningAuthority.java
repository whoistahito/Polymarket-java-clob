package com.polymarket.authentication;

import java.util.Objects;
import java.util.Optional;

/**
 * What the SDK is allowed to do on the caller's behalf: an optional local key for L1 and
 * optional L2 credentials. Absent authority makes the matching operations fail before sending.
 */
public final class SigningAuthority {

    private static final SigningAuthority NONE = new SigningAuthority(null, null, null);

    private final PrivateKeySigner localSigner;
    private final SigningIdentity identity;
    private final ApiCredentials apiCredentials;

    private SigningAuthority(PrivateKeySigner localSigner, SigningIdentity identity,
            ApiCredentials apiCredentials) {
        this.localSigner = localSigner;
        this.identity = identity;
        this.apiCredentials = apiCredentials;
    }

    public static SigningAuthority none() {
        return NONE;
    }

    /** Local signing only; the identity's signer must be the key's own address. */
    public static SigningAuthority signing(PrivateKeySigner signer, SigningIdentity identity) {
        Objects.requireNonNull(signer, "signer");
        Objects.requireNonNull(identity, "identity");
        if (!signer.address().equalsIgnoreCase(identity.signer())) {
            throw new IllegalArgumentException("identity signer " + identity.signer()
                    + " is not the key's address " + signer.address());
        }
        return new SigningAuthority(signer, identity, null);
    }

    public static SigningAuthority apiOnly(ApiCredentials credentials) {
        return new SigningAuthority(null, null, Objects.requireNonNull(credentials, "credentials"));
    }

    public SigningAuthority withApiCredentials(ApiCredentials credentials) {
        return new SigningAuthority(localSigner, identity,
                Objects.requireNonNull(credentials, "credentials"));
    }

    public Optional<PrivateKeySigner> localSigner() {
        return Optional.ofNullable(localSigner);
    }

    public Optional<SigningIdentity> identity() {
        return Optional.ofNullable(identity);
    }

    public Optional<ApiCredentials> apiCredentials() {
        return Optional.ofNullable(apiCredentials);
    }

    PrivateKeySigner requireLocalSigner(String operation) {
        if (localSigner == null) {
            throw new AuthenticationRequiredException(
                    operation + " needs a local signing key; build the SDK with SigningAuthority.signing(...)");
        }
        return localSigner;
    }

    ApiCredentials requireApiCredentials(String operation) {
        if (apiCredentials == null) {
            throw new AuthenticationRequiredException(
                    operation + " needs L2 API credentials; supply them with withApiCredentials(...)");
        }
        return apiCredentials;
    }

    @Override
    public String toString() {
        return "SigningAuthority[localSigner=" + (localSigner != null ? localSigner : "absent")
                + ", identity=" + (identity != null ? identity : "absent")
                + ", apiCredentials=" + (apiCredentials != null ? apiCredentials : "absent") + "]";
    }
}
