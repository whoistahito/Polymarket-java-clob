package com.polymarket.model.gamma;

import lombok.Builder;
import lombok.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.AbstractMap;
import java.util.Map;

@Value
@Builder
public class CommentsRequest {
    ParentEntityType parentEntityType;
    String parentEntityId;
    Integer limit;
    Integer offset;
    String order;
    Boolean ascending;
    Boolean getPositions;
    Boolean holdersOnly;

    public List<Map.Entry<String, String>> toQueryParams() {
        List<Map.Entry<String, String>> params = new ArrayList<>();
        if (parentEntityType != null) params.add(new AbstractMap.SimpleEntry<>("parent_entity_type", parentEntityType.getValue()));
        if (parentEntityId != null) params.add(new AbstractMap.SimpleEntry<>("parent_entity_id", parentEntityId));
        if (limit != null) params.add(new AbstractMap.SimpleEntry<>("limit", limit.toString()));
        if (offset != null) params.add(new AbstractMap.SimpleEntry<>("offset", offset.toString()));
        if (order != null) params.add(new AbstractMap.SimpleEntry<>("order", order));
        if (ascending != null) params.add(new AbstractMap.SimpleEntry<>("ascending", ascending.toString()));
        if (getPositions != null) params.add(new AbstractMap.SimpleEntry<>("get_positions", getPositions.toString()));
        if (holdersOnly != null) params.add(new AbstractMap.SimpleEntry<>("holders_only", holdersOnly.toString()));
        return params;
    }
}
