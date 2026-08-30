package com.polymarket.rewards;

import com.polymarket.authentication.ApiCredentials;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Port for reward reads. The domain declares it; an internal adapter implements it, so no
 * transport type reaches this package.
 */
public interface RewardLedger {

    RewardPage<RewardedMarket> marketRewards(String conditionId, RewardCursor cursor)
            throws IOException;

    RewardPage<CurrentMarketRewards> currentRewards(RewardCursor cursor) throws IOException;

    RewardPage<RewardedMarket> rewardedMarkets(RewardCursor cursor) throws IOException;

    RewardPage<UserEarning> earnings(ApiCredentials credentials, String address, LocalDate date,
            RewardCursor cursor) throws IOException;

    List<UserEarning> totalEarnings(ApiCredentials credentials, String address, LocalDate date)
            throws IOException;

    /** Condition id to the live share of that market's rewards the maker is earning. */
    Map<String, BigDecimal> rewardPercentages(ApiCredentials credentials, String address)
            throws IOException;

    RewardPage<UserRewardedMarket> userRewardedMarkets(ApiCredentials credentials, String address,
            LocalDate date, RewardCursor cursor) throws IOException;
}
