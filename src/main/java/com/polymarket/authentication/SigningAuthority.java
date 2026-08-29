package com.polymarket.authentication;

import java.util.Optional;
import lombok.NonNull;

/**
 * What the SDK may do on the caller's behalf. The Account Signer address authenticates every L2
 * request and is held independently of any local key; the Signing Identity decides which Trading
 * Wallet an order names. Absent authority makes the matching operations fail before sending.
 */
public final class SigningAuthority {

    private static final SigningAuthority NONE = new SigningAuthority(null, null, null, null);

    private final String accountSigner;
    private final PrivateKeySigner accountSignerKey;
    private final SigningIdentity signingIdentity;
    private final ApiCredentials apiCredentials;

    private SigningAuthority(String accountSigner, PrivateKeySigner accountSignerKey,
            SigningIdentity signingIdentity, ApiCredentials apiCredentials) {
        this.accountSigner = accountSigner;
        this.accountSignerKey = accountSignerKey;
        this.signingIdentity = signingIdentity;
        this.apiCredentials = apiCredentials;
    }

    public static SigningAuthority none() {
        return NONE;
    }

    /**
     * API Credentials paired with the Account Signer address they were issued to. Every L2
     * operation works from this alone — no private key and no local signer.
     */
    public static SigningAuthority apiCredentials(
            @NonNull ApiCredentials credentials, @NonNull String accountSigner) {
        return new SigningAuthority(
                Addresses.require(accountSigner, "accountSigner"), null, null, credentials);
    }

    /** The Account Signer's local key plus the Signing Identity that key authorizes orders for. */
    public static SigningAuthority signing(
            @NonNull PrivateKeySigner accountSignerKey, @NonNull SigningIdentity identity) {
        if (!accountSignerKey.address().equalsIgnoreCase(identity.accountSigner())) {
            throw new IllegalArgumentException("Account Signer " + identity.accountSigner()
                    + " is not the key's address " + accountSignerKey.address());
        }
        return new SigningAuthority(
                accountSignerKey.address(), accountSignerKey, identity, null);
    }

    public SigningAuthority withApiCredentials(@NonNull ApiCredentials credentials) {
        return new SigningAuthority(accountSigner, accountSignerKey, signingIdentity, credentials);
    }

    /** The address every L2 header carries, present whether or not its key is held locally. */
    public Optional<String> accountSigner() {
        return Optional.ofNullable(accountSigner);
    }

    public Optional<PrivateKeySigner> accountSignerKey() {
        return Optional.ofNullable(accountSignerKey);
    }

    public Optional<SigningIdentity> signingIdentity() {
        return Optional.ofNullable(signingIdentity);
    }

    public Optional<ApiCredentials> apiCredentials() {
        return Optional.ofNullable(apiCredentials);
    }

    public String requireAccountSigner(String operation) {
        if (accountSigner == null) {
            throw new AuthenticationRequiredException(operation + " needs the Account Signer "
                    + "address; pair it with your API Credentials or supply its local key");
        }
        return accountSigner;
    }

    public ApiCredentials requireApiCredentials(String operation) {
        if (apiCredentials == null) {
            throw new AuthenticationRequiredException(
                    operation + " needs L2 API credentials; supply them with withApiCredentials(...)");
        }
        return apiCredentials;
    }

    /** The Signing Identity an order is authorized against; never used for L2 headers. */
    public SigningIdentity requireSigningIdentity(String operation) {
        if (signingIdentity == null) {
            throw new AuthenticationRequiredException(operation + " needs a Signing Identity; "
                    + "build the SDK with SigningAuthority.signing(...)");
        }
        return signingIdentity;
    }

    PrivateKeySigner requireAccountSignerKey(String operation) {
        if (accountSignerKey == null) {
            throw new AuthenticationRequiredException(operation + " needs the Account Signer's "
                    + "local key; build the SDK with SigningAuthority.signing(...)");
        }
        return accountSignerKey;
    }

    @Override
    public String toString() {
        return "SigningAuthority[accountSigner=" + (accountSigner != null ? accountSigner : "absent")
                + ", accountSignerKey=" + (accountSignerKey != null ? accountSignerKey : "absent")
                + ", signingIdentity=" + (signingIdentity != null ? signingIdentity : "absent")
                + ", apiCredentials=" + (apiCredentials != null ? apiCredentials : "absent") + "]";
    }
}
