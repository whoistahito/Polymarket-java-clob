package com.polymarket.model.gamma;

import lombok.Builder;
import lombok.Value;
import java.util.List;
import java.util.Map;

@Value
@Builder
public class EventTagsRequest {
    String id;

    public List<Map.Entry<String, String>> toQueryParams() {
        return java.util.Collections.emptyList();
    }
}
