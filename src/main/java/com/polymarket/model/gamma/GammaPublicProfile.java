package com.polymarket.model.gamma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GammaPublicProfile(
        String createdAt,
        String proxyWallet,
        String profileImage,
        Boolean displayUsernamePublic,
        String bio,
        String pseudonym,
        String name,
        List<GammaPublicProfileUser> users,
        String xUsername,
        Boolean verifiedBadge
) {}
