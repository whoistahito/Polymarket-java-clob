package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User rewards earnings combined with market config.
 * Mirrors TS {@code UserRewardsEarning}.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserRewardsEarning {

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

    @JsonProperty("market_competitiveness")
    private double marketCompetitiveness;

    @JsonProperty("tokens")
    private List<Map<String, Object>> tokens;

    @JsonProperty("rewards_config")
    private List<Map<String, Object>> rewardsConfig;

    @JsonProperty("maker_address")
    private String makerAddress;

    @JsonProperty("earning_percentage")
    private double earningPercentage;

    @JsonProperty("earnings")
    private List<Map<String, Object>> earnings;
}
