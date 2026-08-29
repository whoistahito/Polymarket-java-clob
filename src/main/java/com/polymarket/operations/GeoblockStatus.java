package com.polymarket.operations;

import java.util.Optional;
import lombok.NonNull;

/**
 * Whether the calling IP may place orders. Absent country/region mean the service did
 * not report them, never "unrestricted".
 */
public record GeoblockStatus(boolean blocked, @NonNull Optional<String> ip,
        @NonNull Optional<String> country, @NonNull Optional<String> region) {

}
