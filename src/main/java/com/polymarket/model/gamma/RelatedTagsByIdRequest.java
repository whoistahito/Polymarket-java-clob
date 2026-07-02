package com.polymarket.model.gamma;

import lombok.Builder;
import lombok.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.AbstractMap;
import java.util.Map;

@Value
@Builder
public class RelatedTagsByIdRequest {
    String id;
    Boolean omitEmpty;
    RelatedTagsStatus status;

    public List<Map.Entry<String, String>> toQueryParams() {
        List<Map.Entry<String, String>> params = new ArrayList<>();
        if (omitEmpty != null) params.add(new AbstractMap.SimpleEntry<>("omit_empty", omitEmpty.toString()));
        if (status != null) params.add(new AbstractMap.SimpleEntry<>("status", status.getValue()));
        return params;
    }
}
