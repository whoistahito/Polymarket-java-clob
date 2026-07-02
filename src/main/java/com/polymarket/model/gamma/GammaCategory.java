package com.polymarket.model.gamma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GammaCategory(
        String id,
        String label,
        String parentCategory,
        String slug,
        String publishedAt,
        String createdAt,
        String updatedAt
) {}
