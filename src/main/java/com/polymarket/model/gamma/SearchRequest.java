package com.polymarket.model.gamma;

import lombok.Builder;
import lombok.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.AbstractMap;
import java.util.Map;

@Value
@Builder
public class SearchRequest {
    String q;
    Boolean cache;
    String eventsStatus;
    Integer limitPerType;
    Integer page;
    List<String> eventsTags;
    Integer keepClosedMarkets;
    String sort;
    Boolean ascending;
    Boolean searchTags;
    Boolean searchProfiles;
    String recurrence;
    List<String> excludeTagId;
    Boolean optimized;

    public List<Map.Entry<String, String>> toQueryParams() {
        List<Map.Entry<String, String>> params = new ArrayList<>();
        if (q != null) params.add(new AbstractMap.SimpleEntry<>("q", q));
        if (cache != null) params.add(new AbstractMap.SimpleEntry<>("cache", cache.toString()));
        if (eventsStatus != null) params.add(new AbstractMap.SimpleEntry<>("events_status", eventsStatus));
        if (limitPerType != null) params.add(new AbstractMap.SimpleEntry<>("limit_per_type", limitPerType.toString()));
        if (page != null) params.add(new AbstractMap.SimpleEntry<>("page", page.toString()));
        if (eventsTags != null) for (String v : eventsTags) params.add(new AbstractMap.SimpleEntry<>("events_tag", v));
        if (keepClosedMarkets != null) params.add(new AbstractMap.SimpleEntry<>("keep_closed_markets", keepClosedMarkets.toString()));
        if (sort != null) params.add(new AbstractMap.SimpleEntry<>("sort", sort));
        if (ascending != null) params.add(new AbstractMap.SimpleEntry<>("ascending", ascending.toString()));
        if (searchTags != null) params.add(new AbstractMap.SimpleEntry<>("search_tags", searchTags.toString()));
        if (searchProfiles != null) params.add(new AbstractMap.SimpleEntry<>("search_profiles", searchProfiles.toString()));
        if (recurrence != null) params.add(new AbstractMap.SimpleEntry<>("recurrence", recurrence));
        if (excludeTagId != null) for (String v : excludeTagId) params.add(new AbstractMap.SimpleEntry<>("exclude_tag_id", v));
        if (optimized != null) params.add(new AbstractMap.SimpleEntry<>("optimized", optimized.toString()));
        return params;
    }
}
