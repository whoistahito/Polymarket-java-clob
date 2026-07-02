package com.polymarket.model.gamma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GammaChat(
        String id,
        String channelId,
        String channelName,
        String channelImage,
        Boolean live,
        String startTime,
        String endTime
) {}
