package com.polymarket.model.gamma;

import lombok.Builder;
import lombok.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.AbstractMap;
import java.util.Map;

@Value
@Builder
public class EventsRequest {
    Integer limit;
    Integer offset;
    String order;
    Boolean ascending;
    List<String> id;
    String tagId;
    List<String> excludeTagId;
    List<String> slug;
    String tagSlug;
    Boolean relatedTags;
    Boolean active;
    Boolean archived;
    Boolean featured;
    Boolean cyom;
    Boolean includeChat;
    Boolean includeTemplate;
    String recurrence;
    Boolean closed;
    String liquidityMin;
    String liquidityMax;
    String volumeMin;
    String volumeMax;
    String startDateMin;
    String startDateMax;
    String endDateMin;
    String endDateMax;
    /** Keyset cursor (from a previous keyset response's {@code next_cursor}); used by {@code /events/keyset}. */
    String afterCursor;

    public List<Map.Entry<String, String>> toQueryParams() {
        List<Map.Entry<String, String>> params = new ArrayList<>();
        if (limit != null) params.add(new AbstractMap.SimpleEntry<>("limit", limit.toString()));
        if (offset != null) params.add(new AbstractMap.SimpleEntry<>("offset", offset.toString()));
        if (order != null) params.add(new AbstractMap.SimpleEntry<>("order", order));
        if (ascending != null) params.add(new AbstractMap.SimpleEntry<>("ascending", ascending.toString()));
        if (id != null) for (String v : id) params.add(new AbstractMap.SimpleEntry<>("id", v));
        if (tagId != null) params.add(new AbstractMap.SimpleEntry<>("tag_id", tagId));
        if (excludeTagId != null) for (String v : excludeTagId) params.add(new AbstractMap.SimpleEntry<>("exclude_tag_id", v));
        if (slug != null) for (String v : slug) params.add(new AbstractMap.SimpleEntry<>("slug", v));
        if (tagSlug != null) params.add(new AbstractMap.SimpleEntry<>("tag_slug", tagSlug));
        if (relatedTags != null) params.add(new AbstractMap.SimpleEntry<>("related_tags", relatedTags.toString()));
        if (active != null) params.add(new AbstractMap.SimpleEntry<>("active", active.toString()));
        if (archived != null) params.add(new AbstractMap.SimpleEntry<>("archived", archived.toString()));
        if (featured != null) params.add(new AbstractMap.SimpleEntry<>("featured", featured.toString()));
        if (cyom != null) params.add(new AbstractMap.SimpleEntry<>("cyom", cyom.toString()));
        if (includeChat != null) params.add(new AbstractMap.SimpleEntry<>("include_chat", includeChat.toString()));
        if (includeTemplate != null) params.add(new AbstractMap.SimpleEntry<>("include_template", includeTemplate.toString()));
        if (recurrence != null) params.add(new AbstractMap.SimpleEntry<>("recurrence", recurrence));
        if (closed != null) params.add(new AbstractMap.SimpleEntry<>("closed", closed.toString()));
        if (liquidityMin != null) params.add(new AbstractMap.SimpleEntry<>("liquidity_min", liquidityMin));
        if (liquidityMax != null) params.add(new AbstractMap.SimpleEntry<>("liquidity_max", liquidityMax));
        if (volumeMin != null) params.add(new AbstractMap.SimpleEntry<>("volume_min", volumeMin));
        if (volumeMax != null) params.add(new AbstractMap.SimpleEntry<>("volume_max", volumeMax));
        if (startDateMin != null) params.add(new AbstractMap.SimpleEntry<>("start_date_min", startDateMin));
        if (startDateMax != null) params.add(new AbstractMap.SimpleEntry<>("start_date_max", startDateMax));
        if (endDateMin != null) params.add(new AbstractMap.SimpleEntry<>("end_date_min", endDateMin));
        if (endDateMax != null) params.add(new AbstractMap.SimpleEntry<>("end_date_max", endDateMax));
        if (afterCursor != null) params.add(new AbstractMap.SimpleEntry<>("after_cursor", afterCursor));
        return params;
    }
}
