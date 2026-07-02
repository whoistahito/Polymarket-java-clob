package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A user notification. Mirrors TS {@code Notification}. */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Notification {

    @JsonProperty("type")
    private int type;

    @JsonProperty("owner")
    private String owner;

    /** Arbitrary notification payload (type-specific content). */
    @JsonProperty("payload")
    private Object payload;
}
