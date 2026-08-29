package com.polymarket.social;

import java.util.Optional;
import lombok.NonNull;

/** One profile matched by a search query; a lighter shape than {@link Profile}. */
public record SearchProfile(
        @NonNull String id,
        @NonNull Optional<String> name,
        @NonNull Optional<String> pseudonym,
        @NonNull Optional<Boolean> displayUsernamePublic,
        @NonNull Optional<String> profileImage,
        @NonNull Optional<String> bio,
        @NonNull Optional<String> proxyWallet,
        @NonNull Optional<Boolean> walletActivated,
        @NonNull Optional<Boolean> closeOnly) {
}
