package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BalanceAllowanceParams {
    @JsonProperty("asset_type")
    AssetType assetType;
    @JsonProperty("token_id")
    String tokenId;

  @JsonProperty("signature_type")
  SignatureType signatureType;

  @JsonProperty("funder")
  String funderAddress;
}
