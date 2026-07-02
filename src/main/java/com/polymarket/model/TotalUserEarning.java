package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Total user earnings aggregated for a day. Mirrors TS {@code TotalUserEarning}. */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TotalUserEarning {

    @JsonProperty("date")
    private String date;

    @JsonProperty("asset_address")
    private String assetAddress;

    @JsonProperty("maker_address")
    private String makerAddress;

    @JsonProperty("earnings")
    private BigDecimal earnings;

    @JsonProperty("asset_rate")
    private BigDecimal assetRate;
}
