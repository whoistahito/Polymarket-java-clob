package com.polymarket.model.gamma;

import lombok.Builder;
import lombok.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.AbstractMap;
import java.util.Map;

@Value
@Builder
public class TagsRequest {
    Integer limit;
    Integer offset;
    String order;
    Boolean ascending;
    Boolean includeTemplate;
    Boolean isCarousel;

    public List<Map.Entry<String, String>> toQueryParams() {
        List<Map.Entry<String, String>> params = new ArrayList<>();
        if (limit != null) params.add(new AbstractMap.SimpleEntry<>("limit", limit.toString()));
        if (offset != null) params.add(new AbstractMap.SimpleEntry<>("offset", offset.toString()));
        if (order != null) params.add(new AbstractMap.SimpleEntry<>("order", order));
        if (ascending != null) params.add(new AbstractMap.SimpleEntry<>("ascending", ascending.toString()));
        if (includeTemplate != null) params.add(new AbstractMap.SimpleEntry<>("include_template", includeTemplate.toString()));
        if (isCarousel != null) params.add(new AbstractMap.SimpleEntry<>("is_carousel", isCarousel.toString()));
        return params;
    }
}
