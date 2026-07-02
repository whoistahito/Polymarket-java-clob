package com.polymarket.model.gamma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GammaComment(
        String id,
        String body,
        String parentEntityType,
        String parentEntityId,
        String parentCommentId,
        String userAddress,
        String replyAddress,
        String createdAt,
        String updatedAt,
        GammaCommentProfile profile,
        List<GammaReaction> reactions,
        Integer reportCount,
        Integer reactionCount
) {}
