package com.polymarket.trading;

import com.polymarket.authentication.PrivateKeySigner;
import com.polymarket.authentication.SigningIdentity;
import java.time.Instant;
import java.util.Optional;
import lombok.NonNull;

/**
 * Everything about who signs and when, explicit and immutable so identical inputs always
 * produce identical signed output. {@code localSigner} must be the key behind {@code identity.accountSigner()}.
 */
public record SigningContext(
        @NonNull SigningIdentity identity,
        @NonNull PrivateKeySigner localSigner,
        long salt,
        @NonNull Instant timestamp,
        @NonNull Optional<String> metadata,
        @NonNull Optional<String> builder) {

    public SigningContext {
        // salt and timestamp are uint256 on the wire; a negative would encode as a wrapped value.
        if (salt < 0) {
            throw new IllegalArgumentException("salt must be unsigned, got: " + salt);
        }
        if (timestamp.getEpochSecond() < 0) {
            throw new IllegalArgumentException("timestamp must be unsigned, got: " + timestamp);
        }
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
