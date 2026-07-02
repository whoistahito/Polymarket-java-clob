package com.polymarket.model.gamma;

import lombok.Builder;
import lombok.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.AbstractMap;
import java.util.Map;

@Value
@Builder
public class MarketsRequest {
    Integer limit;
    Integer offset;
    String order;
    Boolean ascending;
    List<String> id;
    List<String> slug;
    List<String> clobTokenIds;
    List<String> conditionIds;
    String liquidityNumMin;
    String liquidityNumMax;
    String volumeNumMin;
    String volumeNumMax;
    String startDateMin;
    String startDateMax;
    String endDateMin;
    String endDateMax;
    String tagId;
    Boolean relatedTags;
    Boolean cyom;
    Boolean closed;
    List<String> marketMakerAddress;
    String umaResolutionStatus;
    String gameId;
    List<String> sportsMarketTypes;
    String rewardsMinSize;
    List<String> questionIds;
    Boolean includeTag;
    /** Keyset cursor (from a previous keyset response's {@code next_cursor}); used by {@code /markets/keyset}. */
    String afterCursor;

    public List<Map.Entry<String, String>> toQueryParams() {
        List<Map.Entry<String, String>> params = new ArrayList<>();
        if (limit != null) params.add(new AbstractMap.SimpleEntry<>("limit", limit.toString()));
        if (offset != null) params.add(new AbstractMap.SimpleEntry<>("offset", offset.toString()));
        if (order != null) params.add(new AbstractMap.SimpleEntry<>("order", order));
        if (ascending != null) params.add(new AbstractMap.SimpleEntry<>("ascending", ascending.toString()));
        if (id != null) for (String v : id) params.add(new AbstractMap.SimpleEntry<>("id", v));
        if (slug != null) for (String v : slug) params.add(new AbstractMap.SimpleEntry<>("slug", v));
        if (clobTokenIds != null) for (String v : clobTokenIds) params.add(new AbstractMap.SimpleEntry<>("clob_token_ids", v));
        if (conditionIds != null) for (String v : conditionIds) params.add(new AbstractMap.SimpleEntry<>("condition_ids", v));
        if (liquidityNumMin != null) params.add(new AbstractMap.SimpleEntry<>("liquidity_num_min", liquidityNumMin));
        if (liquidityNumMax != null) params.add(new AbstractMap.SimpleEntry<>("liquidity_num_max", liquidityNumMax));
        if (volumeNumMin != null) params.add(new AbstractMap.SimpleEntry<>("volume_num_min", volumeNumMin));
        if (volumeNumMax != null) params.add(new AbstractMap.SimpleEntry<>("volume_num_max", volumeNumMax));
        if (startDateMin != null) params.add(new AbstractMap.SimpleEntry<>("start_date_min", startDateMin));
        if (startDateMax != null) params.add(new AbstractMap.SimpleEntry<>("start_date_max", startDateMax));
        if (endDateMin != null) params.add(new AbstractMap.SimpleEntry<>("end_date_min", endDateMin));
        if (endDateMax != null) params.add(new AbstractMap.SimpleEntry<>("end_date_max", endDateMax));
        if (tagId != null) params.add(new AbstractMap.SimpleEntry<>("tag_id", tagId));
        if (relatedTags != null) params.add(new AbstractMap.SimpleEntry<>("related_tags", relatedTags.toString()));
        if (cyom != null) params.add(new AbstractMap.SimpleEntry<>("cyom", cyom.toString()));
        if (closed != null) params.add(new AbstractMap.SimpleEntry<>("closed", closed.toString()));
        if (marketMakerAddress != null) for (String v : marketMakerAddress) params.add(new AbstractMap.SimpleEntry<>("market_maker_address", v));
        if (umaResolutionStatus != null) params.add(new AbstractMap.SimpleEntry<>("uma_resolution_status", umaResolutionStatus));
        if (gameId != null) params.add(new AbstractMap.SimpleEntry<>("game_id", gameId));
        if (sportsMarketTypes != null) for (String v : sportsMarketTypes) params.add(new AbstractMap.SimpleEntry<>("sports_market_types", v));
        if (rewardsMinSize != null) params.add(new AbstractMap.SimpleEntry<>("rewards_min_size", rewardsMinSize));
        if (questionIds != null) for (String v : questionIds) params.add(new AbstractMap.SimpleEntry<>("question_ids", v));
        if (includeTag != null) params.add(new AbstractMap.SimpleEntry<>("include_tag", includeTag.toString()));
        if (afterCursor != null) params.add(new AbstractMap.SimpleEntry<>("after_cursor", afterCursor));
        return params;
    }
}
