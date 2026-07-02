package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Method used to sign the order.
 */
public enum SignatureType {
    /**
     * Externally Owned Account (Standard Wallet)
     */
    EOA(0),

    /**
     * Polymarket Proxy Wallet
     */
    POLY_PROXY(1),

    /**
     * Gnosis Safe Multisig
     */
    POLY_GNOSIS_SAFE(2),

    /**
     * EIP-1271 smart contract wallet signatures (V2 orders only)
     */
    POLY_1271(3);

    private final int value;

    SignatureType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

  @JsonValue
  public int toJson() {
    return value;
  }

  @JsonCreator
  public static SignatureType fromJson(Object raw) {
    if (raw instanceof Number number) {
      int v = number.intValue();
      for (SignatureType type : values()) {
        if (type.value == v) {
          return type;
        }
      }
    }

    if (raw instanceof String s) {
      String normalized = s.trim();
      for (SignatureType type : values()) {
        if (type.name().equalsIgnoreCase(normalized)) {
          return type;
        }
      }
      try {
        return fromJson(Integer.parseInt(normalized));
      } catch (NumberFormatException ignored) {
        // fall through to default
      }
    }

    return EOA;
  }
}
