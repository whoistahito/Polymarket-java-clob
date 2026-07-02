package com.polymarket.model.gamma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GammaSearchTag(
        String id,
        String label,
        String slug,
        Integer eventCount
) {}
