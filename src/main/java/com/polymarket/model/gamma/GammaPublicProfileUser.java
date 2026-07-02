package com.polymarket.model.gamma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GammaPublicProfileUser(
        String id,
        Boolean creator,
        @JsonProperty("mod") Boolean isMod
) {}
