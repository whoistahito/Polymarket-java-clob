package com.polymarket.operations;

import org.slf4j.Logger;

/** Test-only forbidden dependency: proves the boundary rules actually fail. Never shipped. */
public final class ForeignSignatureLeak {

    /** Proves the boundary bans any foreign type, including a legitimate SDK dependency. */
    public Logger logger() {
        return null;
    }
}
