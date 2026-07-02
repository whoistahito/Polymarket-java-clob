package com.polymarket.model.gamma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GammaRelatedTag(
        String id,
        String tagId,
        String relatedTagId,
        Integer rank
) {}
