package com.polymarket.model.gamma;

import lombok.Builder;
import lombok.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.AbstractMap;
import java.util.Map;

@Value
@Builder
public class MarketBySlugRequest {
    String slug;
    Boolean includeTag;

    public List<Map.Entry<String, String>> toQueryParams() {
        List<Map.Entry<String, String>> params = new ArrayList<>();
        if (includeTag != null) params.add(new AbstractMap.SimpleEntry<>("include_tag", includeTag.toString()));
        return params;
    }
}
