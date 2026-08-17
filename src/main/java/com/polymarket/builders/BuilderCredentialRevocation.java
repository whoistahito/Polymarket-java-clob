package com.polymarket.builders;

import java.util.Objects;
import java.util.Optional;

/** Outcome of revoking the authenticated builder API key. */
public record BuilderCredentialRevocation(boolean revoked, Optional<String> detail) {

    public BuilderCredentialRevocation {
        Objects.requireNonNull(detail, "detail");
    }

    public static BuilderCredentialRevocation succeeded() {
        return new BuilderCredentialRevocation(true, Optional.empty());
    }

    public static BuilderCredentialRevocation failed(String detail) {
        return new BuilderCredentialRevocation(false, Optional.ofNullable(detail));
    }
}
