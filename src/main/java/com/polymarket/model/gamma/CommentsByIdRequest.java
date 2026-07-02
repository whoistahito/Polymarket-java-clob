package com.polymarket.model.gamma;

import lombok.Builder;
import lombok.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.AbstractMap;
import java.util.Map;

@Value
@Builder
public class CommentsByIdRequest {
    String id;
    Boolean getPositions;

    public List<Map.Entry<String, String>> toQueryParams() {
        List<Map.Entry<String, String>> params = new ArrayList<>();
        if (getPositions != null) params.add(new AbstractMap.SimpleEntry<>("get_positions", getPositions.toString()));
        return params;
    }
}
