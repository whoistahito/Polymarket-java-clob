package com.polymarket.authentication;

import java.util.Objects;
import java.util.Optional;

/**
 * Whether the L2 credentials were accepted. A rejection is data, not an exception, so a
 * caller can distinguish bad credentials from a transport failure.
 */
public record ApiKeyValidation(boolean valid, Optional<String> detail) {

    public ApiKeyValidation {
        Objects.requireNonNull(detail, "detail");
    }

    public static ApiKeyValidation accepted() {
        return new ApiKeyValidation(true, Optional.empty());
    }

    public static ApiKeyValidation rejected(String detail) {
        return new ApiKeyValidation(false, Optional.ofNullable(detail));
    }
}
