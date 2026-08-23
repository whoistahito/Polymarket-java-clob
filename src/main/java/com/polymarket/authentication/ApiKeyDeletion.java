package com.polymarket.authentication;

import java.util.Optional;
import lombok.NonNull;

/** Outcome of deleting the authenticated API key. */
public record ApiKeyDeletion(boolean deleted, @NonNull Optional<String> detail) {

    public static ApiKeyDeletion succeeded() {
        return new ApiKeyDeletion(true, Optional.empty());
    }

    public static ApiKeyDeletion failed(String detail) {
        return new ApiKeyDeletion(false, Optional.ofNullable(detail));
    }
}
