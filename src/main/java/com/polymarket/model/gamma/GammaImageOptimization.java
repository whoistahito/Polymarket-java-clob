package com.polymarket.model.gamma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GammaImageOptimization(
        String id,
        String imageUrlSource,
        String imageUrlOptimized,
        Long imageSizeKbSource,
        Long imageSizeKbOptimized,
        Boolean imageOptimizedComplete,
        String imageOptimizedLastUpdated,
        String relId,
        String field,
        String relname
) {}
