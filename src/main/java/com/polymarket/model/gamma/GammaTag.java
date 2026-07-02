package com.polymarket.model.gamma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GammaTag(
        String id,
        String label,
        String slug,
        Boolean forceShow,
        String publishedAt,
        String createdAt,
        String updatedAt,
        Boolean forceHide,
        Boolean isCarousel,
        Boolean requiresTranslation
) {}
