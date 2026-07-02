package com.polymarket.model.gamma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GammaTemplate(
        String id,
        String eventTitle,
        String eventSlug,
        String eventImage,
        String marketTitle,
        String description,
        String resolutionSource,
        Boolean negRisk,
        String sortBy,
        Boolean showMarketImages,
        String seriesSlug,
        List<String> outcomes
) {}
