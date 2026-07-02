package com.polymarket.model.gamma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GammaSportsMetadata(
        String id,
        String sport,
        String image,
        String resolution,
        String ordering,
        List<String> tags,
        String series,
        String createdAt
) {}
