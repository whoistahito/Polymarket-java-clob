package com.polymarket.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a Polymarket prediction market.
 */
public class Market {
    private final String conditionId;
    private final String question;
    private final Instant endDate;
    private final BigDecimal minSize;
    private final boolean acceptingOrders;
    private final boolean restricted;
    private final List<String> outcomes;
    /** Typed token list. Mirrors TS {@code Market.tokens: Token[]}. */
    private final List<Token> tokens;
    private final BigDecimal tickSize;
    private final Integer feeRateBps;

    private Market(Builder builder) {
        this.conditionId = builder.conditionId;
        this.question = builder.question;
        this.endDate = builder.endDate;
        this.minSize = builder.minSize;
        this.acceptingOrders = builder.acceptingOrders;
        this.restricted = builder.restricted;
        this.outcomes = builder.outcomes;
        this.tokens = builder.tokens;
        this.tickSize = builder.tickSize;
        this.feeRateBps = builder.feeRateBps;
    }

    public String getConditionId() {
        return conditionId;
    }

    public String getQuestion() {
        return question;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public BigDecimal getMinSize() {
        return minSize;
    }

    public boolean isAcceptingOrders() {
        return acceptingOrders;
    }

    public boolean isRestricted() {
        return restricted;
    }

    public List<String> getOutcomes() {
        return outcomes;
    }

    /**
     * Returns the typed token list.
     *
     * @see #getTokenIds() for backward-compatible token ID access
     */
    public List<Token> getTokens() {
        return tokens;
    }

    /**
     * Returns only the token IDs extracted from {@link #getTokens()}.
     *
     * <p>Backward-compatible helper for callers that previously used the old
     * {@code List<String> tokenIds} field.
     */
    public List<String> getTokenIds() {
        if (tokens == null || tokens.isEmpty()) {
            return Collections.emptyList();
        }
        return tokens.stream()
                .map(Token::getTokenId)
                .collect(Collectors.toList());
    }

    public BigDecimal getTickSize() {
        return tickSize;
    }

    public Integer getFeeRateBps() {
        return feeRateBps;
    }

    /**
     * Check if this market is tradeable (accepting orders and not restricted).
     */
    public boolean isTradeable() {
        return acceptingOrders && !restricted;
    }

    /**
     * Get hours until market closes.
     */
    public long getHoursUntilClose() {
        if (endDate == null) {
            return Long.MAX_VALUE;
        }
        long secondsUntilClose = endDate.getEpochSecond() - Instant.now().getEpochSecond();
        return secondsUntilClose / 3600;
    }

    @Override
    public String toString() {
        return "Market{" +
                "conditionId='" + conditionId + '\'' +
                ", question='" + question + '\'' +
                ", endDate=" + endDate +
                ", minSize=" + minSize +
                ", acceptingOrders=" + acceptingOrders +
                ", restricted=" + restricted +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String conditionId;
        private String question;
        private Instant endDate;
        private BigDecimal minSize = BigDecimal.ONE;
        private boolean acceptingOrders = true;
        private boolean restricted = false;
        private List<String> outcomes;
        private List<Token> tokens;
        private BigDecimal tickSize;
        private Integer feeRateBps;

        public Builder conditionId(String conditionId) {
            this.conditionId = conditionId;
            return this;
        }

        public Builder question(String question) {
            this.question = question;
            return this;
        }

        public Builder endDate(Instant endDate) {
            this.endDate = endDate;
            return this;
        }

        public Builder minSize(BigDecimal minSize) {
            this.minSize = minSize;
            return this;
        }

        public Builder acceptingOrders(boolean acceptingOrders) {
            this.acceptingOrders = acceptingOrders;
            return this;
        }

        public Builder restricted(boolean restricted) {
            this.restricted = restricted;
            return this;
        }

        public Builder outcomes(List<String> outcomes) {
            this.outcomes = outcomes;
            return this;
        }

        /** Sets the typed token list. Replaces the old {@code tokenIds(List<String>)} setter. */
        public Builder tokens(List<Token> tokens) {
            this.tokens = tokens;
            return this;
        }

        /**
         * Backward-compatible setter; wraps each token ID into a minimal {@link Token}.
         *
         * @deprecated Prefer {@link #tokens(List)} with fully typed {@link Token} objects.
         */
        @Deprecated
        public Builder tokenIds(List<String> tokenIds) {
            if (tokenIds == null) {
                this.tokens = null;
            } else {
                this.tokens = tokenIds.stream()
                        .map(id -> Token.builder().tokenId(id).build())
                        .collect(Collectors.toList());
            }
            return this;
        }

        public Builder tickSize(BigDecimal tickSize) {
            this.tickSize = tickSize;
            return this;
        }

        public Builder feeRateBps(Integer feeRateBps) {
            this.feeRateBps = feeRateBps;
            return this;
        }

        public Market build() {
            return new Market(this);
        }
    }
}
