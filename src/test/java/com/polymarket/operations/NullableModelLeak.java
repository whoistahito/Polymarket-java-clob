package com.polymarket.operations;

import java.util.Optional;

/** Test-only fixture: a public model whose components accept null, which the rule must reject. */
public record NullableModelLeak(String required, Optional<String> optional, int count) {
}
