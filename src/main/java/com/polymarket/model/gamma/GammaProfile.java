package com.polymarket.model.gamma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GammaProfile(
        String id,
        String name,
        String pseudonym,
        Boolean displayUsernamePublic,
        String profileImage,
        String bio,
        String proxyWallet,
        Boolean walletActivated,
        Boolean isCloseOnly
) {}
