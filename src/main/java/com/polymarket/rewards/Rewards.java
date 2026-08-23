package com.polymarket.rewards;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.SigningAuthority;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Public reward reads. One call returns one page; walking pages is the caller's decision. */
public final class Rewards {

    private final SigningAuthority authority;
    private final RewardLedger ledger;

    public Rewards(SigningAuthority authority, RewardLedger ledger) {
        this.authority = Objects.requireNonNull(authority, "authority");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
    }

    /** First page of the reward programmes configured on one market. Needs no credentials. */
    public RewardPage<RewardedMarket> marketRewards(String conditionId) throws IOException {
        return marketRewards(conditionId, RewardCursor.first());
    }

    public RewardPage<RewardedMarket> marketRewards(String conditionId, RewardCursor cursor)
            throws IOException {
        return ledger.marketRewards(requireConditionId(conditionId),
                Objects.requireNonNull(cursor, "cursor"));
    }

    /**
     * Every page for one market. Bounded in practice: a market carries a handful of present and
     * future programmes, and revisiting a cursor ends the walk so no server can cycle it.
     */
    public List<RewardedMarket> allMarketRewards(String conditionId) throws IOException {
        List<RewardedMarket> markets = new ArrayList<>();
        Set<RewardCursor> visited = new LinkedHashSet<>();
        Optional<RewardCursor> cursor = Optional.of(RewardCursor.first());
        while (cursor.isPresent() && visited.add(cursor.get())) {
            RewardPage<RewardedMarket> page = marketRewards(conditionId, cursor.get());
            markets.addAll(page.items());
            cursor = page.nextCursor();
        }
        return List.copyOf(markets);
    }

    /**
     * First page of every market with an active reward programme. No all-pages convenience:
     * the whole exchange is unbounded, so the caller decides how far to walk.
     */
    public RewardPage<CurrentMarketRewards> currentRewards() throws IOException {
        return currentRewards(RewardCursor.first());
    }

    public RewardPage<CurrentMarketRewards> currentRewards(RewardCursor cursor) throws IOException {
        return ledger.currentRewards(Objects.requireNonNull(cursor, "cursor"));
    }

    /** First page of rewarded markets with their trading metrics. Unbounded, so page by page. */
    public RewardPage<RewardedMarket> rewardedMarkets() throws IOException {
        return rewardedMarkets(RewardCursor.first());
    }

    public RewardPage<RewardedMarket> rewardedMarkets(RewardCursor cursor) throws IOException {
        return ledger.rewardedMarkets(Objects.requireNonNull(cursor, "cursor"));
    }

    /** First page of the maker's per-market earnings for one day. L2-authenticated. */
    public RewardPage<UserEarning> earnings(LocalDate date) throws IOException {
        return earnings(date, RewardCursor.first());
    }

    public RewardPage<UserEarning> earnings(LocalDate date, RewardCursor cursor)
            throws IOException {
        return ledger.earnings(credentials("earnings"), address("earnings"),
                Objects.requireNonNull(date, "date"), Objects.requireNonNull(cursor, "cursor"));
    }

    /** The maker's earnings for one day summed per reward asset. Not paginated by the CLOB. */
    public List<UserEarning> totalEarnings(LocalDate date) throws IOException {
        return ledger.totalEarnings(credentials("totalEarnings"), address("totalEarnings"),
                Objects.requireNonNull(date, "date"));
    }

    /** Live share of each market's rewards the maker is earning, keyed by condition id. */
    public Map<String, BigDecimal> rewardPercentages() throws IOException {
        return ledger.rewardPercentages(
                credentials("rewardPercentages"), address("rewardPercentages"));
    }

    /** First page of rewarded markets with the maker's earnings on each. L2-authenticated. */
    public RewardPage<UserRewardedMarket> userRewardedMarkets(LocalDate date) throws IOException {
        return userRewardedMarkets(date, RewardCursor.first());
    }

    public RewardPage<UserRewardedMarket> userRewardedMarkets(LocalDate date, RewardCursor cursor)
            throws IOException {
        return ledger.userRewardedMarkets(credentials("userRewardedMarkets"),
                address("userRewardedMarkets"), Objects.requireNonNull(date, "date"),
                Objects.requireNonNull(cursor, "cursor"));
    }

    private ApiCredentials credentials(String operation) {
        return authority.requireApiCredentials(operation);
    }

    private String address(String operation) {
        return authority.requireAccountSigner(operation);
    }

    private static String requireConditionId(String conditionId) {
        Objects.requireNonNull(conditionId, "conditionId");
        if (conditionId.isBlank()) {
            throw new IllegalArgumentException("conditionId must not be blank");
        }
        return conditionId;
    }
}
