package com.polymarket.builders;

import java.util.Optional;
import lombok.NonNull;

/** Outcome of revoking the authenticated builder API key. */
public record BuilderCredentialRevocation(boolean revoked, @NonNull Optional<String> detail) {


    public static BuilderCredentialRevocation succeeded() {
        return new BuilderCredentialRevocation(true, Optional.empty());
    }

    public static BuilderCredentialRevocation failed(String detail) {
        return new BuilderCredentialRevocation(false, Optional.ofNullable(detail));
    }
}
