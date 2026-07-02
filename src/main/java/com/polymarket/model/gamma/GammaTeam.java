package com.polymarket.model.gamma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GammaTeam(
        String id,
        String name,
        String league,
        String record,
        String logo,
        String abbreviation,
        String alias,
        String createdAt,
        String updatedAt,
        String color,
        String providerId
) {}
