package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A reward configuration for a market including tokens and rewards config.
 * Mirrors TS {@code MarketReward}.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketReward {

    @JsonProperty("condition_id")
    private String conditionId;

    @JsonProperty("question")
    private String question;

    @JsonProperty("market_slug")
    private String marketSlug;

    @JsonProperty("event_slug")
    private String eventSlug;

    @JsonProperty("image")
    private String image;

    @JsonProperty("rewards_max_spread")
    private double rewardsMaxSpread;

    @JsonProperty("rewards_min_size")
    private double rewardsMinSize;

    @JsonProperty("tokens")
    private List<Map<String, Object>> tokens;

    @JsonProperty("rewards_config")
    private List<Map<String, Object>> rewardsConfig;
}
