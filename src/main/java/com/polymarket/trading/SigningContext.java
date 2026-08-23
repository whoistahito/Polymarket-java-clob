package com.polymarket.trading;

import com.polymarket.authentication.PrivateKeySigner;
import com.polymarket.authentication.SigningIdentity;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything about who signs and when, explicit and immutable so identical inputs always
 * produce identical signed output. {@code localSigner} must be the key behind {@code identity.accountSigner()}.
 */
public record SigningContext(
        SigningIdentity identity,
        PrivateKeySigner localSigner,
        long salt,
        Instant timestamp,
        Optional<String> metadata,
        Optional<String> builder) {

    public SigningContext {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(localSigner, "localSigner");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(builder, "builder");
        if (!localSigner.address().equalsIgnoreCase(identity.accountSigner())) {
            throw new IllegalArgumentException("localSigner address " + localSigner.address()
                    + " is not identity.accountSigner() " + identity.accountSigner());
        }
    }

    public static SigningContext of(
            SigningIdentity identity, PrivateKeySigner localSigner, long salt, Instant timestamp) {
        return new SigningContext(
                identity, localSigner, salt, timestamp, Optional.empty(), Optional.empty());
    }

    public SigningContext withMetadata(String metadataHex) {
        return new SigningContext(identity, localSigner, salt, timestamp,
                Optional.of(metadataHex), builder);
    }

    public SigningContext withBuilder(String builderHex) {
        return new SigningContext(identity, localSigner, salt, timestamp,
                metadata, Optional.of(builderHex));
    }
}
