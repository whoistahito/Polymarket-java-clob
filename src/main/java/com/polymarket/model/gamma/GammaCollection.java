package com.polymarket.model.gamma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GammaCollection(
        String id,
        String ticker,
        String slug,
        String title,
        String subtitle,
        String collectionType,
        String description,
        Boolean active,
        Boolean closed,
        Boolean archived,
        Boolean featured,
        Boolean restricted,
        String publishedAt,
        String createdAt,
        String updatedAt
) {}
