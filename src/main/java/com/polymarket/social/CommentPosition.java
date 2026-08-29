package com.polymarket.social;

import java.math.BigDecimal;
import java.util.Optional;
import lombok.NonNull;

/** A commenter's holding in the token their comment is attached to, when {@code get_positions} was asked for. */
public record CommentPosition(@NonNull Optional<String> tokenId, Optional<BigDecimal> positionSize) {

}
