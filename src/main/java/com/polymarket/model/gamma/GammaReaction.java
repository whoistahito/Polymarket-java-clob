package com.polymarket.model.gamma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GammaReaction(
        String id,
        String commentId,
        String reactionType,
        String icon,
        String userAddress,
        String createdAt
) {}
