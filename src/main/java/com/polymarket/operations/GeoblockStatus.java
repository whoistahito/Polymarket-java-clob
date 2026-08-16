package com.polymarket.operations;

import java.util.Objects;
import java.util.Optional;

/**
 * Whether the calling IP may place orders. Absent country/region mean the service did
 * not report them, never "unrestricted".
 */
public record GeoblockStatus(
        boolean blocked, Optional<String> ip, Optional<String> country, Optional<String> region) {

    public GeoblockStatus {
        Objects.requireNonNull(ip, "ip");
        Objects.requireNonNull(country, "country");
        Objects.requireNonNull(region, "region");
    }
}
