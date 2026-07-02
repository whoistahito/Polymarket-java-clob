package com.polymarket.model.gamma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GammaEventCreator(
        String id,
        String creatorName,
        String creatorHandle,
        String creatorUrl,
        String creatorImage,
        String createdAt,
        String updatedAt
) {}
