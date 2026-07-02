package com.polymarket.model.gamma;

import lombok.Builder;
import lombok.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.AbstractMap;
import java.util.Map;

@Value
@Builder
public class TeamsRequest {
    Integer limit;
    Integer offset;
    String order;
    Boolean ascending;
    List<String> league;
    List<String> name;
    List<String> abbreviation;

    public List<Map.Entry<String, String>> toQueryParams() {
        List<Map.Entry<String, String>> params = new ArrayList<>();
        if (limit != null) params.add(new AbstractMap.SimpleEntry<>("limit", limit.toString()));
        if (offset != null) params.add(new AbstractMap.SimpleEntry<>("offset", offset.toString()));
        if (order != null) params.add(new AbstractMap.SimpleEntry<>("order", order));
        if (ascending != null) params.add(new AbstractMap.SimpleEntry<>("ascending", ascending.toString()));
        if (league != null) for (String v : league) params.add(new AbstractMap.SimpleEntry<>("league", v));
        if (name != null) for (String v : name) params.add(new AbstractMap.SimpleEntry<>("name", v));
        if (abbreviation != null) for (String v : abbreviation) params.add(new AbstractMap.SimpleEntry<>("abbreviation", v));
        return params;
    }
}
