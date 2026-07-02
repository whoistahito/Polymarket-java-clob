package com.polymarket.model.gamma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GammaClobReward(
        String id,
        String assetAddress,
        String conditionId,
        String startDate,
        String endDate,
        BigDecimal rewardsAmount,
        BigDecimal rewardsDailyRate
) {}
