package com.polymarket.model.gamma;

import lombok.Builder;
import lombok.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.AbstractMap;
import java.util.Map;

@Value
@Builder
public class SeriesListRequest {
    Integer limit;
    Integer offset;
    String order;
    Boolean ascending;
    List<String> slug;
    Boolean closed;
    Boolean includeChat;
    String recurrence;
    List<String> categoriesIds;
    List<String> categoriesLabels;
    Boolean excludeEvents;

    public List<Map.Entry<String, String>> toQueryParams() {
        List<Map.Entry<String, String>> params = new ArrayList<>();
        if (limit != null) params.add(new AbstractMap.SimpleEntry<>("limit", limit.toString()));
        if (offset != null) params.add(new AbstractMap.SimpleEntry<>("offset", offset.toString()));
        if (order != null) params.add(new AbstractMap.SimpleEntry<>("order", order));
        if (ascending != null) params.add(new AbstractMap.SimpleEntry<>("ascending", ascending.toString()));
        if (slug != null) for (String v : slug) params.add(new AbstractMap.SimpleEntry<>("slug", v));
        if (closed != null) params.add(new AbstractMap.SimpleEntry<>("closed", closed.toString()));
        if (includeChat != null) params.add(new AbstractMap.SimpleEntry<>("include_chat", includeChat.toString()));
        if (recurrence != null) params.add(new AbstractMap.SimpleEntry<>("recurrence", recurrence));
        if (categoriesIds != null) for (String v : categoriesIds) params.add(new AbstractMap.SimpleEntry<>("categories_ids", v));
        if (categoriesLabels != null) for (String v : categoriesLabels) params.add(new AbstractMap.SimpleEntry<>("categories_labels", v));
        if (excludeEvents != null) params.add(new AbstractMap.SimpleEntry<>("exclude_events", excludeEvents.toString()));
        return params;
    }
}
