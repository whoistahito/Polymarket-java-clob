package com.polymarket.authentication;

import java.util.Objects;
import java.util.Optional;

/** Outcome of deleting the authenticated API key. */
public record ApiKeyDeletion(boolean deleted, Optional<String> detail) {

    public ApiKeyDeletion {
        Objects.requireNonNull(detail, "detail");
    }

    public static ApiKeyDeletion succeeded() {
        return new ApiKeyDeletion(true, Optional.empty());
    }

    public static ApiKeyDeletion failed(String detail) {
        return new ApiKeyDeletion(false, Optional.ofNullable(detail));
    }
}
