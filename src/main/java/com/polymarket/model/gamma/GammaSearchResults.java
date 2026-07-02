package com.polymarket.model.gamma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GammaSearchResults(
        List<GammaEvent> events,
        List<GammaSearchTag> tags,
        List<GammaProfile> profiles,
        GammaPagination pagination
) {}
