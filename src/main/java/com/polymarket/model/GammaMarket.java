package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.polymarket.util.JsonEmbeddedListDeserializer;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * A Polymarket market as returned by the Gamma REST API ({@code /markets}).
 *
 * <p>Several fields ({@code clobTokenIds}, {@code outcomes}, {@code outcomePrices}) are
 * transmitted by the API as JSON-embedded strings, e.g. {@code "[\"123\",\"456\"]"}.
 * The {@link JsonEmbeddedListDeserializer} unpacks them automatically so callers always
 * receive a plain {@code List<String>}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GammaMarket(
        String id,
        String question,
        String endDate,
        BigDecimal volume24hr,
        Boolean acceptingOrders,
        Boolean active,
        Boolean closed,
        Boolean enableOrderBook,

        @JsonDeserialize(using = JsonEmbeddedListDeserializer.class)
        List<String> clobTokenIds,

        @JsonDeserialize(using = JsonEmbeddedListDeserializer.class)
        List<String> outcomes,

        @JsonDeserialize(using = JsonEmbeddedListDeserializer.class)
        List<String> outcomePrices,

        /**
         * Minimum price increment for this market, exact (Ticket 024). Null when the response
         * omitted it, so a caller can fail closed rather than assume a tick.
         */
        BigDecimal orderPriceMinTickSize,

        /** Minimum order size in shares, exact (Ticket 024). Null when the response omitted it. */
        BigDecimal orderMinSize
) {
  /**
   * Source-compatible constructor for callers written before the market rules were modelled
   * (Ticket 024). Leaves both rules {@code null}, which reads as "unknown" rather than a default.
   */
  public GammaMarket(
      String id,
      String question,
      String endDate,
      BigDecimal volume24hr,
      Boolean acceptingOrders,
      Boolean active,
      Boolean closed,
      Boolean enableOrderBook,
      List<String> clobTokenIds,
      List<String> outcomes,
      List<String> outcomePrices) {
    this(id, question, endDate, volume24hr, acceptingOrders, active, closed, enableOrderBook,
        clobTokenIds, outcomes, outcomePrices, null, null);
  }

  /**
   * This market's order-construction rules as one typed value (Ticket 024).
   *
   * <p>Prefer this over reading the two fields separately: {@link MarketRules#isValid()} states in
   * one call whether an order may be built for this market at all.
   */
  public MarketRules marketRules() {
    return MarketRules.of(orderPriceMinTickSize, orderMinSize);
  }

  // JavaBean-style accessors for callers that prefer get...() methods.
  public BigDecimal getOrderPriceMinTickSize() {
    return orderPriceMinTickSize();
  }

  public BigDecimal getOrderMinSize() {
    return orderMinSize();
  }

  public String getId() {
    return id();
  }

  public String getQuestion() {
    return question();
  }

  public String getEndDate() {
    return endDate();
  }

  public BigDecimal getVolume24hr() {
    return volume24hr();
  }

  public Boolean getAcceptingOrders() {
    return acceptingOrders();
  }

  public Boolean getActive() {
    return active();
  }

  public Boolean getClosed() {
    return closed();
  }

  public Boolean getEnableOrderBook() {
    return enableOrderBook();
  }

  public List<String> getClobTokenIds() {
    return tokenIds();
  }

  public List<String> getTokenIds() {
    return tokenIds();
  }

  public List<String> getOutcomes() {
    return outcomes != null ? outcomes : List.of();
  }

  public List<String> getOutcomePrices() {
    return outcomePrices != null ? outcomePrices : List.of();
  }

    /** Returns the CLOB token IDs, guaranteed non-null. */
    public List<String> tokenIds() {
        return clobTokenIds != null ? clobTokenIds : List.of();
    }

  /** Returns the CLOB token id for the YES outcome, or null when unavailable. */
  public String getYesTokenId() {
    return getTokenIdByOutcome(BinaryOutcome.YES).orElse(null);
  }

  /** Returns the CLOB token id for the NO outcome, or null when unavailable. */
  public String getNoTokenId() {
    return getTokenIdByOutcome(BinaryOutcome.NO).orElse(null);
  }

  /** Returns the token id for a binary outcome (YES/NO), if present. */
  public Optional<String> getTokenIdByOutcome(BinaryOutcome outcome) {
    if (outcome == null) {
      return Optional.empty();
    }
    List<String> labels = getOutcomes();
    List<String> tokens = tokenIds();
    for (int i = 0; i < labels.size() && i < tokens.size(); i++) {
      if (outcome.matches(labels.get(i))) {
        return Optional.ofNullable(tokens.get(i));
      }
    }
    return Optional.empty();
  }

    /** Returns true if this market has at least one tradeable CLOB token. */
    public boolean hasClobTokens() {
        return !tokenIds().isEmpty();
    }
}
