package com.polymarket.operations;

import org.apache.commons.lang3.tuple.Pair;

/** Test-only forbidden dependency: proves the boundary rules actually fail. Never shipped. */
public final class ForeignSignatureLeak {

    public Pair<String, String> pair() {
        return null;
    }
}
