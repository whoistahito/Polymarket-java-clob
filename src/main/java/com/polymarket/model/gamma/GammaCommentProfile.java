package com.polymarket.model.gamma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GammaCommentProfile(
        String name,
        String pseudonym,
        Boolean displayUsernamePublic,
        String bio,
        Boolean isMod,
        Boolean isCreator,
        String proxyWallet,
        String baseAddress,
        String profileImage,
        List<GammaCommentPosition> positions
) {}
