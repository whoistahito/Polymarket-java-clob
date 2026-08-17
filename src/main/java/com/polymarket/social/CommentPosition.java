package com.polymarket.social;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/** A commenter's holding in the token their comment is attached to, when {@code get_positions} was asked for. */
public record CommentPosition(Optional<String> tokenId, Optional<BigDecimal> positionSize) {

    public CommentPosition {
        Objects.requireNonNull(tokenId, "tokenId");
        Objects.requireNonNull(positionSize, "positionSize");
    }
}
